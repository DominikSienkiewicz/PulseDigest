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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.MonthMentions;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandAggregator;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandPulse;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandHistoryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechDemandProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.QuotaErrors;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the monthly tech-demand pulse from the Hacker News "Who is hiring?" thread.
 *
 * <p>Finds the latest such thread via Algolia, and only when it is fresh (within
 * {@code lookbackDays}) aggregates how often each tracked technology appears across its hiring posts.
 * The previous month is read from {@code tech_demand_history} rather than re-scraped: the snapshot
 * was already computed and stored when that thread was current, so a second pass over a thousand
 * comments buys nothing. Only a month absent from history (first run, or a changed vocabulary) falls
 * back to fetching the older thread. Returns {@link Optional#empty()} when disabled,
 * when no fresh thread exists, or when nothing clears {@code minMentions}. HTTP quota/rate-limit
 * failures propagate (via {@link QuotaErrors}) so the source is flagged for the "exhausted limits"
 * banner.
 */
@Slf4j
@Service
public class TechDemandAdapter {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern TITLE_MONTH = Pattern.compile("\\(([^)]+)\\)");
    private static final Locale PL = Locale.of("pl", "PL");
    private static final int THREAD_SEARCH_HITS = 20;

    private final ObjectMapper objectMapper;
    private final TechDemandProperties props;
    private final TechDemandHistoryPort historyPort;
    private RestClient restClient;

    public TechDemandAdapter(ObjectMapper objectMapper, TechDemandProperties props,
                             TechDemandHistoryPort historyPort) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.historyPort = historyPort;
    }

    /**
     * Identifies the technology list that produced a set of counts. Counts gathered under a different
     * vocabulary are a different series, so they must never be compared month over month.
     */
    private String vocabularyVersion() {
        return Integer.toHexString(String.join(",", props.technologies()).hashCode());
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
            List<Story> threads = findWhoIsHiringThreads();
            if (threads.isEmpty()) {
                return Optional.empty();
            }
            Story current = threads.get(0);
            if (!isFresh(current.createdAtI())) {
                log.info("Latest 'Who is hiring' thread is older than {} days — skipping pulse.", props.lookbackDays());
                return Optional.empty();
            }

            List<String> currentPosts = fetchTopLevelComments(current.objectID());
            Map<String, Integer> currentCounts = TechDemandAggregator.countMentions(currentPosts, props.technologies());
            MonthMentions currentMonth = new MonthMentions(monthLabel(current), currentCounts, currentPosts.size());

            String vocabulary = vocabularyVersion();
            historyPort.save(currentMonth, vocabulary);
            MonthMentions previousMonth = previousMonth(threads, vocabulary);

            TechDemandSignal signal = TechDemandPulse.assemble(
                    "https://news.ycombinator.com/item?id=" + current.objectID(),
                    currentMonth,
                    previousMonth,
                    props.priorityTechnologies(), props.minMentions(), props.maxTechnologies());

            if (signal.isEmpty()) {
                log.info("Tech-demand pulse: no technology cleared minMentions={} — skipping.", props.minMentions());
                return Optional.empty();
            }
            log.info("Tech-demand pulse: {} technologies from {} hiring posts (prev month: {}).",
                    signal.entries().size(), signal.totalPostings(), previousMonth != null);
            return Optional.of(signal);

        } catch (Exception e) {
            QuotaErrors.rethrowIfQuota(e);
            log.warn("Tech-demand pulse fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The previous month's snapshot: from storage when it was recorded under the same vocabulary,
     * otherwise re-scraped once and recorded, so the fallback pays for itself the next run.
     */
    private MonthMentions previousMonth(List<Story> threads, String vocabulary) {
        if (threads.size() < 2) {
            return null;
        }
        Story previous = threads.get(1);
        String label = monthLabel(previous);
        Optional<MonthMentions> stored = historyPort.findByMonth(label, vocabulary);
        if (stored.isPresent()) {
            log.info("Tech-demand: previous month {} read from history — thread not re-fetched.", label);
            return stored.get();
        }
        List<String> previousPosts = fetchTopLevelComments(previous.objectID());
        MonthMentions previousMonth = new MonthMentions(label,
                TechDemandAggregator.countMentions(previousPosts, props.technologies()), previousPosts.size());
        historyPort.save(previousMonth, vocabulary);
        return previousMonth;
    }

    // Returns matching "Who is hiring?" stories newest-first (index 0 = current, 1 = previous month).
    private List<Story> findWhoIsHiringThreads() {
        var uri = UriComponentsBuilder.fromUriString(props.baseUrl() + "/search_by_date")
                .queryParam("query", "who is hiring")
                .queryParam("tags", "story,author_whoishiring")
                .queryParam("hitsPerPage", THREAD_SEARCH_HITS)
                .build()
                .toUri();
        String json = restClient.get().uri(uri).retrieve().body(String.class);
        SearchResponse response = parse(json, SearchResponse.class);
        if (response == null || response.hits() == null) {
            return List.of();
        }
        return response.hits().stream()
                .filter(h -> h.objectID() != null && h.createdAtI() != null)
                .filter(h -> h.title() != null && h.title().toLowerCase(Locale.ROOT).contains("who is hiring"))
                .toList();
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
        return ageSeconds <= props.lookbackDays() * 86_400L;
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
