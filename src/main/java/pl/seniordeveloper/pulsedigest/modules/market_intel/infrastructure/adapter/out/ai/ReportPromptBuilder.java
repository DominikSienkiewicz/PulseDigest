package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CategoryPreference;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptItemMeta;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PreScoringPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.PreScoringProperties;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReportPromptBuilder {

    private static final String KEY_SOURCE = "source";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";
    private static final String KEY_ENGAGEMENT = "engagement_score";
    private static final String KEY_TEXT_PREVIEW = "text_preview";
    // ~50 titles ≈ 1–2k prompt tokens. Enough to cover three editions of Mon/Wed/Fri without
    // letting the block grow with the archive.
    private static final int MAX_PUBLISHED_TITLES = 50;

    private final ObjectMapper objectMapper;
    private final PublishedUrlsPort publishedUrlsPort;
    private final DedupProperties dedupProperties;
    private final InterestProfileProperties interestProfile;
    private final FeedbackPort feedbackPort;
    private final FeedbackProperties feedbackProperties;
    private final PreScoringPort preScoringPort;
    private final PreScoringProperties preScoringProperties;

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        log.debug("Loaded system prompt ({} chars) from {}", systemPrompt.length(), systemPromptResource.getFilename());
    }

    public String buildSystemPrompt() {
        // Profil odbiorcy doklejany z interest-profile (jedno źródło prawdy) — rubryka scoringu
        // w system-prompt.txt odsyła do tej sekcji zamiast hardcodować persony.
        return systemPrompt + "\n\n== PROFIL ODBIORCY ==\n" + interestProfile.persona() + "\n";
    }

    /** Full-intake prompt — every source capped at its own budget, then trimmed to {@code TOTAL_CAP}. */
    public String buildUserPrompt(ResearchResult research) {
        return buildPrompt(research).userPrompt();
    }

    /**
     * Reduced-intake prompt: same payload, trimmed to at most {@code totalCap} items by pre-score.
     * Used to recover from a truncated model response by re-sending fewer items.
     */
    public String buildUserPrompt(ResearchResult research, int totalCap) {
        return buildPrompt(research, totalCap).userPrompt();
    }

    /** Full-intake prompt plus the trusted metadata of every item it carries. */
    public PromptPayload buildPrompt(ResearchResult research) {
        return buildPrompt(research, PromptItemSelector.TOTAL_CAP);
    }

    /** Reduced-intake prompt (at most {@code totalCap} items) plus its trusted item metadata. */
    public PromptPayload buildPrompt(ResearchResult research, int totalCap) {
        List<Map<String, Object>> all = new ArrayList<>();
        List.of(
                mapItems(research.tweets(), PromptItemMapper::mapTweet),
                mapItems(research.hackerNewsPosts(), PromptItemMapper::mapHackerNews),
                mapItems(research.githubRepos(), PromptItemMapper::mapGithubRepo),
                mapItems(research.rssItems(), PromptItemMapper::mapRssItem),
                mapItems(research.redditPosts(), PromptItemMapper::mapRedditPost),
                mapItems(research.papers(), PromptItemMapper::mapPaper),
                mapItems(research.releases(), PromptItemMapper::mapRelease),
                mapItems(research.huggingFaceModels(), PromptItemMapper::mapHuggingFaceModel),
                mapItems(research.productHuntPosts(), PromptItemMapper::mapProductHuntPost),
                mapItems(research.securityAdvisories(), PromptItemMapper::mapSecurityAdvisory),
                mapItems(research.jepUpdates(), PromptItemMapper::mapJepUpdate),
                mapItems(research.cncfProjectUpdates(), PromptItemMapper::mapCncfProjectUpdate),
                mapItems(research.radarEntries(), PromptItemMapper::mapRadarEntry),
                mapItems(research.conferenceTalks(), PromptItemMapper::mapConferenceTalk),
                mapItems(research.labAnnouncements(), PromptItemMapper::mapLabAnnouncement),
                mapItems(research.socialPosts(), PromptItemMapper::mapSocialPost)
        ).forEach(all::addAll);

        all = filterAlreadyPublished(all);
        List<Map<String, Object>> payload = preScore(PromptItemSelector.selectTopItems(all, totalCap));

        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Prompt payload: {} itemów wybranych z {} (tweets={}, hn={}, gh={}, rss={}, reddit={},"
                            + " papers={}, releases={}, hf={}, ph={}, advisories={},"
                            + " jep={}, cncf={}, radar={}, talks={}, social={}, labs={})",
                    payload.size(), all.size(),
                    research.tweets().size(),
                    research.hackerNewsPosts().size(),
                    research.githubRepos().size(),
                    research.rssItems().size(),
                    research.redditPosts().size(),
                    research.papers().size(),
                    research.releases().size(),
                    research.huggingFaceModels().size(),
                    research.productHuntPosts().size(),
                    research.securityAdvisories().size(),
                    research.jepUpdates().size(),
                    research.cncfProjectUpdates().size(),
                    research.radarEntries().size(),
                    research.conferenceTalks().size(),
                    research.socialPosts().size(),
                    research.labAnnouncements().size());
            return new PromptPayload(
                    alreadyPublishedBlock() + readerPreferencesBlock()
                            + "Oto posty z ostatnich kilku dni:\n\n" + json,
                    inputMetaOf(payload));
        } catch (JsonProcessingException e) {
            log.error("Błąd serializacji payloadu: {}", e.getMessage());
            return new PromptPayload("Oto posty z ostatnich kilku dni:\n\n[]", Map.of());
        }
    }

    /**
     * Lists the stories already sent, by title, so the model can recognize a re-run of the same story
     * from a different outlet — something URL dedup structurally cannot see. Empty (and free) when
     * there is no history or cross-edition dedup is off.
     */
    private String alreadyPublishedBlock() {
        if (!dedupProperties.enabled()) {
            return "";
        }
        List<String> titles = publishedUrlsPort.recentlyPublishedTitles(
                dedupProperties.lookbackDays(), MAX_PUBLISHED_TITLES);
        if (titles.isEmpty()) {
            return "";
        }
        return "== JUŻ OPUBLIKOWANE ==\n"
                + "Te historie ukazały się w poprzednich wydaniach. Pomiń item, który opisuje tę samą\n"
                + "historię — nawet jeśli pochodzi z innego źródła i ma inny URL. Wyjątek: nowy, istotny\n"
                + "rozwój wydarzeń (np. GA po RC, wycofanie, kolejna wersja).\n"
                + titles.stream().map(t -> "- " + t).collect(Collectors.joining("\n"))
                + "\n\n";
    }

    /**
     * Halves the payload with a cheap triage model before the expensive one reads it. Disabled — or
     * when the triage call returns no opinion — the payload passes through untouched.
     */
    private List<Map<String, Object>> preScore(List<Map<String, Object>> payload) {
        if (!preScoringProperties.enabled() || payload.size() <= preScoringProperties.keep()) {
            return payload;
        }
        List<PreScoringCandidate> candidates = payload.stream()
                .map(item -> new PreScoringCandidate(
                        (String) item.get(KEY_URL),
                        (String) item.get(KEY_TITLE),
                        (String) item.get(KEY_SOURCE),
                        ((Number) item.get(KEY_ENGAGEMENT)).intValue()))
                .toList();
        List<Map<String, Object>> kept =
                PreScoringTriage.triage(payload, preScoringPort.score(candidates), preScoringProperties.keep());
        log.info("Pre-scoring triage: {} → {} itemów do głównego modelu", payload.size(), kept.size());
        return kept;
    }

    /**
     * Tells the model which topics the reader has repeatedly asked for more or less of.
     *
     * <p>Only categories the reader voted on {@code >= 3} times net appear — one stray click is noise,
     * not a taste. The wording is a soft preference rather than an exclusion: a muted topic that
     * genuinely matters must still be able to surface, or the loop turns into a trap the category can
     * never climb out of. Empty while the receiver still writes no {@code category}, which is exactly
     * how this degrades until that receiver is deployed.
     */
    private String readerPreferencesBlock() {
        if (!feedbackProperties.enabled()) {
            return "";
        }
        Map<String, Integer> netVotes = feedbackPort.netVotesByCategory(feedbackProperties.lookbackDays());
        List<String> wanted = expressedCategories(netVotes, true);
        List<String> unwanted = expressedCategories(netVotes, false);
        if (wanted.isEmpty() && unwanted.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("== PREFERENCJE CZYTELNIKA ==\n")
                .append("Wynikają z jego kliknięć 👍/👎 w poprzednich wydaniach. To preferencja, NIE filtr:\n")
                .append("naprawdę ważny item z nielubianej kategorii nadal ma się przebić.\n");
        if (!wanted.isEmpty()) {
            sb.append("Chce więcej: ").append(String.join(", ", wanted)).append('\n');
        }
        if (!unwanted.isEmpty()) {
            sb.append("Chce mniej: ").append(String.join(", ", unwanted)).append('\n');
        }
        return sb.append('\n').toString();
    }

    private static List<String> expressedCategories(Map<String, Integer> netVotes, boolean positive) {
        return netVotes.entrySet().stream()
                .filter(e -> CategoryPreference.isExpressed(e.getValue()))
                .filter(e -> positive == e.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Indexes the outgoing payload by canonical URL. On a duplicate URL the first item wins — the
     * selector already ordered them by source budget, so the earlier entry is the better-ranked one.
     */
    private static Map<String, PromptItemMeta> inputMetaOf(List<Map<String, Object>> payload) {
        Map<String, PromptItemMeta> meta = new HashMap<>();
        for (Map<String, Object> item : payload) {
            if (item.get(KEY_URL) instanceof String url && !url.isBlank()) {
                meta.putIfAbsent(UrlCanonicalizer.canonicalize(url), new PromptItemMeta(
                        (String) item.get(KEY_SOURCE),
                        ((Number) item.get(KEY_ENGAGEMENT)).intValue()));
            }
        }
        return Map.copyOf(meta);
    }

    private static <T> List<Map<String, Object>> mapItems(List<T> items, Function<T, Map<String, Object>> fn) {
        return items.stream().map(fn).toList();
    }

    // Drops items whose canonical URL should be suppressed before LLM scoring — cross-edition
    // duplicates (already published in a recent edition) and reader down-votes (feedback "less like
    // this"). Filters the unified payload by URL, so every source is covered uniformly — including
    // tweets, whose URL is synthesized above even though the Tweet record has none.
    private List<Map<String, Object>> filterAlreadyPublished(List<Map<String, Object>> all) {
        Set<String> suppressed = collectSuppressedUrls();
        if (suppressed.isEmpty()) {
            return all;
        }
        List<Map<String, Object>> fresh = all.stream()
                .filter(item -> {
                    Object url = item.get(KEY_URL);
                    return !(url instanceof String s) || !suppressed.contains(UrlCanonicalizer.canonicalize(s));
                })
                .toList();
        int dropped = all.size() - fresh.size();
        if (dropped > 0) {
            log.info("Pominięto {} itemów przed scoringiem (cross-edition dedup + feedback)", dropped);
        }
        return fresh;
    }

    private Set<String> collectSuppressedUrls() {
        Set<String> suppressed = new HashSet<>();
        if (dedupProperties.enabled()) {
            suppressed.addAll(publishedUrlsPort.recentlyPublishedUrls(dedupProperties.lookbackDays()));
        }
        if (feedbackProperties.enabled()) {
            suppressed.addAll(feedbackPort.downvotedUrls(feedbackProperties.lookbackDays()));
        }
        return suppressed;
    }

}
