package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandAggregator;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechDemandProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.QuotaErrors;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the monthly tech-demand pulse from the Hacker News "Who is hiring?" thread.
 *
 * <p>Finds the latest such thread via Algolia, and only when it is fresh (within
 * {@code lookbackDays}) fetches its top-level comments — one per hiring post — and aggregates how
 * often each tracked technology is mentioned. Returns {@link Optional#empty()} when disabled, when
 * no fresh thread exists, or when nothing clears {@code minMentions}, so the digest simply omits the
 * section. HTTP quota/rate-limit failures propagate (via {@link QuotaErrors}) so the source is
 * flagged for the "exhausted limits" banner.
 */
@Slf4j
@Service
public class TechDemandAdapter {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern TITLE_MONTH = Pattern.compile("\\(([^)]+)\\)");
    private static final Locale PL = Locale.of("pl", "PL");

    private final ObjectMapper objectMapper;
    private final TechDemandProperties props;
    private RestClient restClient;

    public TechDemandAdapter(ObjectMapper objectMapper, TechDemandProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<TechDemandSignal> fetchTechDemand() {
        if (!props.enabled()) {
            log.info("Tech-demand pulse disabled — skipping.");
            return Optional.empty();
        }
        try {
            Optional<Story> thread = findLatestWhoIsHiringThread();
            if (thread.isEmpty()) {
                return Optional.empty();
            }
            Story story = thread.get();
            if (!isFresh(story.createdAtI())) {
                log.info("Latest 'Who is hiring' thread is older than {} days — skipping pulse.", props.lookbackDays());
                return Optional.empty();
            }

            List<String> postings = fetchTopLevelComments(story.objectID());
            List<TechDemandEntry> entries = TechDemandAggregator.aggregate(
                    postings, props.technologies(), props.minMentions(), props.maxTechnologies());
            if (entries.isEmpty()) {
                log.info("Tech-demand pulse: no technology cleared minMentions={} — skipping.", props.minMentions());
                return Optional.empty();
            }

            TechDemandSignal signal = new TechDemandSignal(
                    monthLabel(story), "https://news.ycombinator.com/item?id=" + story.objectID(),
                    postings.size(), entries);
            log.info("Tech-demand pulse: {} technologies from {} hiring posts.", entries.size(), postings.size());
            return Optional.of(signal);

        } catch (Exception e) {
            QuotaErrors.rethrowIfQuota(e);
            log.warn("Tech-demand pulse fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Story> findLatestWhoIsHiringThread() {
        var uri = UriComponentsBuilder.fromUriString(props.baseUrl() + "/search_by_date")
                .queryParam("query", "who is hiring")
                .queryParam("tags", "story,author_whoishiring")
                .queryParam("hitsPerPage", 10)
                .build()
                .toUri();
        String json = restClient.get().uri(uri).retrieve().body(String.class);
        SearchResponse response = parse(json, SearchResponse.class);
        if (response == null || response.hits() == null) {
            return Optional.empty();
        }
        return response.hits().stream()
                .filter(h -> h.objectID() != null && h.createdAtI() != null)
                .filter(h -> h.title() != null && h.title().toLowerCase(Locale.ROOT).contains("who is hiring"))
                .findFirst();
    }

    private List<String> fetchTopLevelComments(String objectId) {
        var uri = UriComponentsBuilder.fromUriString(props.baseUrl() + "/items/" + objectId)
                .build()
                .toUri();
        String json = restClient.get().uri(uri).retrieve().body(String.class);
        ThreadItem item = parse(json, ThreadItem.class);
        if (item == null || item.children() == null) {
            return List.of();
        }
        return item.children().stream()
                .map(ChildComment::text)
                .filter(t -> t != null && !t.isBlank())
                .map(TechDemandAdapter::stripHtml)
                .limit(props.maxComments())
                .toList();
    }

    private boolean isFresh(long createdAtEpochSeconds) {
        long ageSeconds = Instant.now().getEpochSecond() - createdAtEpochSeconds;
        return ageSeconds <= (long) props.lookbackDays() * 86_400L;
    }

    private String monthLabel(Story story) {
        if (story.title() != null) {
            var matcher = TITLE_MONTH.matcher(story.title());
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        var date = Instant.ofEpochSecond(story.createdAtI()).atZone(ZoneOffset.UTC).toLocalDate();
        return date.getMonth().getDisplayName(TextStyle.FULL, PL) + " " + date.getYear();
    }

    private static String stripHtml(String html) {
        String text = HTML_TAG.matcher(html).replaceAll(" ");
        return text.replace("&amp;", "&")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&#x2F;", "/")
                .replace("&#x27;", "'");
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return json == null || json.isBlank() ? null : objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Tech-demand parse failed: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<Story> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Story(
            String title,
            @JsonProperty("objectID") String objectID,
            @JsonProperty("created_at_i") Long createdAtI) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ThreadItem(List<ChildComment> children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChildComment(String text) {
    }
}
