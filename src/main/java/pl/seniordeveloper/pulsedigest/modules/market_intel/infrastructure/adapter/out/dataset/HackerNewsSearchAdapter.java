package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.AsyncConfig;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fetches trending discussions from Hacker News via Algolia search API.
 * Runs single-keyword queries in parallel to work around Algolia v1's
 * TF-IDF ranking behavior, where multi-term OR queries become too restrictive.
 */
@Slf4j
@Service
public class HackerNewsSearchAdapter {

    private final ObjectMapper objectMapper;
    private final ReportProperties reportProperties;
    private final Executor taskExecutor;
    private RestClient restClient;
    private ReportProperties.HackerNewsProperties props;

    @Autowired
    public HackerNewsSearchAdapter(
            ObjectMapper objectMapper,
            ReportProperties reportProperties,
            @Qualifier(AsyncConfig.DATA_FETCH_EXECUTOR) Executor taskExecutor) {
        this.objectMapper = objectMapper;
        this.reportProperties = reportProperties;
        this.taskExecutor = taskExecutor;
    }

    HackerNewsSearchAdapter(ObjectMapper objectMapper, ReportProperties reportProperties) {
        this(objectMapper, reportProperties, Executors.newVirtualThreadPerTaskExecutor());
    }

    @PostConstruct
    void init() {
        this.props = reportProperties.hackerNews();
        this.restClient = ExternalRestClients.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<HackerNewsPost> fetchTopDiscussions() {
        if (props == null || props.baseUrl() == null || props.keywords() == null
                || props.keywords().isEmpty()) {
            log.warn("Brak konfiguracji hacker-news w application.yaml. Pomijam HN.");
            return List.of();
        }

        log.info("Rozpoczynam przeszukiwanie Hacker News dla {} keywords", props.keywords().size());

        try {
            long since = Instant.now().minusSeconds(86_400).getEpochSecond();

            List<CompletableFuture<List<HnHit>>> futures = props.keywords().stream()
                    .map(kw -> CompletableFuture.supplyAsync(() -> fetchKeyword(kw, since), taskExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            List<HnHit> allHits = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(Collection::stream)
                    .toList();

            LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
            List<HnHit> deduped = allHits.stream()
                    .filter(hit -> seenUrls.add(dedupeKey(hit)))
                    .toList();

            int minScore = props.minScore() > 0 ? props.minScore() : 25;
            int limit = props.limit() > 0 ? props.limit() : 15;

            List<HackerNewsPost> posts = deduped.stream()
                    .filter(hit -> hit.points() != null && hit.points() >= minScore)
                    .limit(limit)
                    .map(hit -> new HackerNewsPost(
                            hit.title() != null ? hit.title() : "(Brak tytułu)",
                            hit.url() != null ? hit.url() : "https://news.ycombinator.com/item?id=" + hit.objectID(),
                            hit.points() != null ? hit.points() : 0
                    ))
                    .toList();

            log.info("Znaleziono {} postów na Hacker News (score >= {}).", posts.size(), minScore);
            return posts;

        } catch (Exception e) {
            log.error("Błąd podczas pobierania danych z Hacker News algolia API: {}", e.getMessage());
            return List.of();
        }
    }

    private List<HnHit> fetchKeyword(String keyword, long since) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                    .queryParam("query", keyword)
                    .queryParam("tags", "story")
                    .queryParam("hitsPerPage", 50)
                    .queryParam("numericFilters", "created_at_i>" + since)
                    .build()
                    .toUri();

            String rawJson = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            return parseResponse(rawJson);
        } catch (Exception e) {
            log.warn("HN keyword '{}' fetch failed: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    List<HnHit> parseResponse(String rawJson) {
        try {
            HnResponse response = objectMapper.readValue(rawJson, HnResponse.class);

            if (response.hits() == null) {
                return List.of();
            }
            return response.hits();
        } catch (Exception e) {
            log.warn("HN parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String dedupeKey(HnHit hit) {
        return hit.url() != null ? hit.url() : "hn://" + hit.objectID();
    }

    // -------------------------------------------------------------------------
    // Algolia response structures
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HnResponse(List<HnHit> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnHit(
            String title,
            String url,
            Integer points,
            String objectID
    ) {
    }
}
