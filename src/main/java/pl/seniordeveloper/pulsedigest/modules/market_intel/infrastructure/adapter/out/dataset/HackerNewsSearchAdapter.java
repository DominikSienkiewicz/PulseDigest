package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pobiera innowacyjne dyskusje (early trends) z API HackerNews (Algolia).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class HackerNewsSearchAdapter {

    private static final String API_URL = "https://hn.algolia.com/api/v1/search";

    private final ObjectMapper objectMapper;
    private final ReportProperties reportProperties;
    private RestClient restClient;
    private ReportProperties.HackerNewsProperties props;

    @PostConstruct
    void init() {
        this.props = reportProperties.hackerNews();
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<HackerNewsPost> fetchTopDiscussions() {
        if (props == null || props.query() == null || props.query().isBlank()) {
            log.warn("Brak konfiguracji hacker-news.query w application.yaml. Pomijam HN.");
            return List.of();
        }

        log.info("Rozpoczynam przeszukiwanie Hacker News dla: {}", props.query());

        try {
            long since = Instant.now().minusSeconds(86_400).getEpochSecond();
            URI uri = UriComponentsBuilder.fromUriString(API_URL)
                    .queryParam("query", props.query())
                    .queryParam("tags", "story")
                    .queryParam("hitsPerPage", 50)
                    .queryParam("numericFilters", "created_at_i>" + since)
                    .build()
                    .toUri();

            String rawJson = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            HnResponse response = objectMapper.readValue(rawJson, HnResponse.class);

            if (response.hits() == null || response.hits().isEmpty()) {
                log.info("Brak pasujących wyników Hacker News.");
                return List.of();
            }

            int minScore = props.minScore() > 0 ? props.minScore() : 50;
            int limit = props.limit() > 0 ? props.limit() : 10;

            List<HackerNewsPost> posts = response.hits().stream()
                    .filter(hit -> hit.points() != null && hit.points() >= minScore)
                    .limit(limit)
                    .map(hit -> new HackerNewsPost(
                            hit.title() != null ? hit.title() : "(Brak tytułu)",
                            hit.url() != null ? hit.url() : "https://news.ycombinator.com/item?id=" + hit.objectID(),
                            hit.points() != null ? hit.points() : 0
                    ))
                    .collect(Collectors.toList());

            log.info("Znaleziono {} postów na Hacker News (score >= {}).", posts.size(), minScore);
            return posts;

        } catch (Exception e) {
            log.error("Błąd podczas pobierania danych z Hacker News algolia API: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Struktura algolii
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HnResponse(List<HnHit> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HnHit(
            String title,
            String url,
            Integer points,
            String objectID
    ) {
    }
}
