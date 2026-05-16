package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubProperties;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Szuka najciekawszych repozytoriów na GitHubie jako dowód adopcji technologicznej (kod > słowa).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GithubSearchAdapter {

    private static final String API_URL = "https://api.github.com/search/repositories";

    private final ObjectMapper objectMapper;
    private final GithubProperties props;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "IT-Market-Report-App")
                .build();
    }

    public List<GithubRepo> fetchTrendingRepos() {
        if (props == null || props.query() == null || props.query().isBlank()) {
            log.warn("Brak konfiguracji github.query w application.yaml. Pomijam GitHuba.");
            return List.of();
        }

        String pushedSince = LocalDate.now().minusDays(1).toString();
        String query = props.query() + " pushed:>=" + pushedSince;
        log.info("Przeszukuję GitHub: {}", query);
        int limit = props.limit() > 0 ? props.limit() : 5;

        try {
            URI uri = UriComponentsBuilder.fromUriString(API_URL)
                    .queryParam("q", query)
                    .queryParam("sort", "stars")
                    .queryParam("order", "desc")
                    .queryParam("per_page", limit)
                    .build()
                    .toUri();

            String rawJson = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            GhResponse response = objectMapper.readValue(rawJson, GhResponse.class);

            if (response.items() == null || response.items().isEmpty()) {
                log.info("Nie znaleziono pasujących repozytoriów dla GH Search.");
                return List.of();
            }

            List<GithubRepo> repos = response.items().stream()
                    .limit(limit)
                    .map(it -> new GithubRepo(
                            it.fullName() != null ? it.fullName() : "Brak nazwy",
                            it.description() != null ? it.description() : "",
                            it.stargazersCount() != null ? it.stargazersCount() : 0,
                            it.htmlUrl() != null ? it.htmlUrl() : ""
                    ))
                    .collect(Collectors.toList());

            log.info("Znaleziono {} repozytoriów na platformie GitHub.", repos.size());
            return repos;

        } catch (Exception e) {
            log.error("Błąd podczas pobierania danych z GitHub Search API: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Struktura API GitHub
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GhResponse(List<GhItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GhItem(
            @JsonProperty("full_name") String fullName,
            String description,
            @JsonProperty("stargazers_count") Integer stargazersCount,
            @JsonProperty("html_url") String htmlUrl
    ) {
    }
}
