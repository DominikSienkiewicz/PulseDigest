package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LabAnnouncementsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes official announcements from AI-lab blogs and newsrooms (Anthropic, OpenAI, Google Gemini).
 *
 * <p>None of these sites expose RSS, so each {@link LabAnnouncementsProperties.Strategy} handles a
 * different way of locating publish dates — see the strategy enum for details. A failing source is
 * logged and skipped; only when <em>every</em> source fails does the adapter throw, so a partial
 * outage still produces a digest while a total outage surfaces as a FAILED source-health entry.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LabAnnouncementsAdapter {

    private static final Pattern SANITY_ENTRY = Pattern.compile(
            "\"publishedOn\":\"([0-9T:.\\-Z]+)\""
                    + ".{0,400}?\"current\":\"([a-z0-9-]+)\""
                    + "(?:.{0,2000}?\"summary\":\"([^\"]*)\")?"
                    + ".{0,2000}?\"title\":\"([^\"]+)\"",
            Pattern.DOTALL);
    private static final Pattern JSONLD_DATE = Pattern.compile(
            "\"datePublished\"\\s*:\\s*\"([^\"]+)\"");
    // Reluctant, length-bounded [^>]{1,300}? matches up to the first attribute inside one <meta> tag.
    // The bound keeps backtracking linear (an unbounded reluctant gap is still polynomial on tags
    // that never reach the required attribute); 300 chars far exceeds any real <meta> attribute list.
    private static final Pattern OG_TITLE = Pattern.compile(
            "<meta[^>]{1,300}?property=\"og:title\"[^>]{1,300}?content=\"([^\"]+)\""
                    + "|<meta[^>]{1,300}?content=\"([^\"]+)\"[^>]{1,300}?property=\"og:title\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]{1,300}?name=\"description\"[^>]{1,300}?content=\"([^\"]+)\""
                    + "|<meta[^>]{1,300}?content=\"([^\"]+)\"[^>]{1,300}?name=\"description\"",
            Pattern.CASE_INSENSITIVE);
    // developers.openai.com card: link, then a short date, title (line-clamp-2), description (line-clamp-3).
    private static final Pattern OPENAI_CARD = Pattern.compile(
            "href=\"(/blog/[a-z0-9-]+)\""
                    + ".{0,800}?text-secondary[^>]*>([A-Za-z]{3} \\d{1,2})</div>"
                    + ".{0,300}?line-clamp-2\">([^<]+)</div>"
                    + "(?:.{0,300}?line-clamp-3[^>]*>([^<]*)</p>)?",
            Pattern.DOTALL);

    private static final DateTimeFormatter MONTH_DAY_YEAR =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_DAY_SHORT_YEAR =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

    private final LabAnnouncementsProperties properties;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; PulseDigest/1.0 lab-watch)")
                .defaultHeader("Accept", "text/html,application/xhtml+xml")
                .build();
    }

    public List<LabAnnouncement> fetchAnnouncements() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(properties.lookbackHours());
        List<LabAnnouncement> result = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;

        for (LabAnnouncementsProperties.Source source : properties.sources()) {
            try {
                result.addAll(fetchSource(source, cutoff));
            } catch (Exception e) {
                failed++;
                lastError = e;
                log.warn("Lab announcement source '{}' failed: {}", source.name(), e.getMessage());
            }
        }

        if (failed > 0 && failed == properties.sources().size()) {
            throw new IllegalStateException(
                    "All " + failed + " lab announcement sources failed; last error: "
                            + (lastError != null ? lastError.getMessage() : "unknown"),
                    lastError);
        }
        log.info("Lab announcements: {} posts in last {}h ({}/{} sources ok)",
                result.size(), properties.lookbackHours(),
                properties.sources().size() - failed, properties.sources().size());
        return result;
    }

    List<LabAnnouncement> fetchSource(LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        return switch (source.strategy()) {
            case SANITY -> fetchSanity(source, cutoff);
            case JSONLD -> fetchJsonLd(source, cutoff);
            case OPENAI_DEV -> fetchOpenAiDev(source, cutoff);
        };
    }

    // ── SANITY: anthropic.com/news, anthropic.com/engineering ────────────────

    private List<LabAnnouncement> fetchSanity(LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        String html = get(source.listingUrl());
        if (html == null || html.isBlank()) {
            return List.of();
        }
        // Sanity data is embedded JSON-escaped (\" instead of "). Unescape once, then plain regex.
        String unescaped = html.replace("\\\"", "\"").replace("\\\\", "\\");

        String baseHost = baseHostOf(source.listingUrl());
        String pathPrefix = URI.create(source.listingUrl()).getPath();
        List<LabAnnouncement> result = new ArrayList<>();
        Set<String> seenSlugs = new LinkedHashSet<>();

        Matcher m = SANITY_ENTRY.matcher(unescaped);
        while (m.find()) {
            sanityEntry(m, seenSlugs, baseHost, pathPrefix, source, cutoff).ifPresent(result::add);
        }
        return result;
    }

    private Optional<LabAnnouncement> sanityEntry(Matcher m, Set<String> seenSlugs, String baseHost,
            String pathPrefix, LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        String slug = m.group(2);
        if (!seenSlugs.add(slug)) {
            return Optional.empty();
        }
        LocalDateTime publishedAt = parseFlexibleDate(m.group(1));
        if (publishedAt == null || publishedAt.isBefore(cutoff)) {
            return Optional.empty();
        }
        String summary = m.group(3) != null ? m.group(3).trim() : "";
        return Optional.of(new LabAnnouncement(
                m.group(4).trim(),
                baseHost + pathPrefix + "/" + slug,
                summary,
                source.name(),
                publishedAt));
    }

    // ── JSONLD: claude.com/blog, blog.google (listing → per-post datePublished) ──

    private List<LabAnnouncement> fetchJsonLd(LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        String listing = get(source.listingUrl());
        if (listing == null || listing.isBlank()) {
            return List.of();
        }
        if (source.articleLinkRegex() == null || source.articleLinkRegex().isBlank()) {
            log.warn("JSONLD source '{}' has no articleLinkRegex configured", source.name());
            return List.of();
        }

        String baseHost = baseHostOf(source.listingUrl());
        List<String> postUrls = extractLinks(listing, source.articleLinkRegex(), baseHost);
        LocalDateTime cutoffDate = cutoff.toLocalDate().atStartOfDay();

        List<LabAnnouncement> result = new ArrayList<>();
        int checked = 0;
        for (String postUrl : postUrls) {
            if (checked++ >= properties.postFetchLimit()) {
                break;
            }
            try {
                parseJsonLdPost(get(postUrl), postUrl, source.name(), cutoffDate).ifPresent(result::add);
            } catch (Exception e) {
                log.warn("JSONLD post fetch failed [{}]: {}", postUrl, e.getMessage());
            }
        }
        return result;
    }

    Optional<LabAnnouncement> parseJsonLdPost(String html, String url, String sourceName, LocalDateTime cutoff) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Matcher dateMatch = JSONLD_DATE.matcher(html);
        if (!dateMatch.find()) {
            return Optional.empty();
        }
        LocalDateTime publishedAt = parseFlexibleDate(dateMatch.group(1));
        if (publishedAt == null || publishedAt.isBefore(cutoff)) {
            return Optional.empty();
        }
        String title = firstGroup(OG_TITLE.matcher(html));
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String summary = firstGroup(META_DESCRIPTION.matcher(html));
        return Optional.of(new LabAnnouncement(
                title.trim(), url, summary != null ? summary.trim() : "", sourceName, publishedAt));
    }

    // ── OPENAI_DEV: developers.openai.com/blog (cards inline in listing) ──────

    private List<LabAnnouncement> fetchOpenAiDev(LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        String html = get(source.listingUrl());
        if (html == null || html.isBlank()) {
            return List.of();
        }
        return parseOpenAiDev(html, source, cutoff);
    }

    List<LabAnnouncement> parseOpenAiDev(String html, LabAnnouncementsProperties.Source source, LocalDateTime cutoff) {
        String baseHost = baseHostOf(source.listingUrl());
        LocalDateTime cutoffDate = cutoff.toLocalDate().atStartOfDay();
        List<LabAnnouncement> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Matcher m = OPENAI_CARD.matcher(html);
        while (m.find()) {
            openAiCard(m, seen, baseHost, source, cutoffDate).ifPresent(result::add);
        }
        return result;
    }

    private Optional<LabAnnouncement> openAiCard(Matcher m, Set<String> seen, String baseHost,
            LabAnnouncementsProperties.Source source, LocalDateTime cutoffDate) {
        String slug = m.group(1);
        if (!seen.add(slug)) {
            return Optional.empty();
        }
        LocalDateTime publishedAt = parseShortDate(m.group(2));
        if (publishedAt == null || publishedAt.isBefore(cutoffDate)) {
            return Optional.empty();
        }
        String summary = m.group(4) != null ? m.group(4).trim() : "";
        return Optional.of(new LabAnnouncement(
                m.group(3).trim(), baseHost + slug, summary, source.name(), publishedAt));
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private String get(String url) {
        // HTTP errors propagate to fetchAnnouncements, which aggregates per-source failures.
        return restClient.get().uri(URI.create(url)).retrieve().body(String.class);
    }

    private List<String> extractLinks(String html, String regex, String baseHost) {
        Set<String> links = new LinkedHashSet<>();
        Matcher m = Pattern.compile(regex).matcher(html);
        while (m.find()) {
            String link = m.group(1);
            links.add(link.startsWith("http") ? link : baseHost + link);
        }
        return List.copyOf(links);
    }

    /** Parses ISO offset ({@code 2026-05-19T17:45:00Z}), ISO date, or {@code "May 19, 2026"}. */
    private LocalDateTime parseFlexibleDate(String raw) {
        String s = raw.trim();
        try {
            return ZonedDateTime.parse(s).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception _) {
            // not an offset datetime
        }
        try {
            return LocalDate.parse(s, MONTH_DAY_YEAR).atStartOfDay();
        } catch (Exception _) {
            // not a "Month d, yyyy" date
        }
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception _) {
            return null;
        }
    }

    /** Parses a yearless {@code "Feb 4"} date, assuming the most recent matching year. */
    private LocalDateTime parseShortDate(String raw) {
        int year = Year.now(ZoneOffset.UTC).getValue();
        try {
            LocalDate parsed = LocalDate.parse(raw.trim() + " " + year, MONTH_DAY_SHORT_YEAR);
            if (parsed.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                parsed = LocalDate.parse(raw.trim() + " " + (year - 1), MONTH_DAY_SHORT_YEAR);
            }
            return parsed.atStartOfDay();
        } catch (Exception _) {
            return null;
        }
    }

    private String firstGroup(Matcher m) {
        if (!m.find()) {
            return null;
        }
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null && !m.group(i).isBlank()) {
                return m.group(i);
            }
        }
        return null;
    }

    private String baseHostOf(String url) {
        try {
            URI uri = URI.create(url);
            String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return uri.getScheme() + "://" + authority;
        } catch (Exception _) {
            return "";
        }
    }
}
