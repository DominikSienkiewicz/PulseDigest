package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches OSS release notes from the GitHub Releases API.
 */
@Slf4j
@Service
public class GithubReleasesAdapter {

    private static final String BASE_URL = "https://api.github.com";

    private final ReportProperties.GithubReleasesProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public GithubReleasesAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.githubReleases(), objectMapper);
    }

    GithubReleasesAdapter(ReportProperties.GithubReleasesProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<SoftwareRelease> fetchLatestReleases() {
        List<SoftwareRelease> result = new ArrayList<>();
        for (String repo : properties.repositories()) {
            result.addAll(fetchReleasesForRepo(repo));
        }
        log.info("GitHub Releases: {} releases from {} repos", result.size(), properties.repositories().size());
        return result;
    }

    private List<SoftwareRelease> fetchReleasesForRepo(String repoFullName) {
        String[] parts = repoFullName.split("/", 2);
        try {
            String json = restClient.get()
                    .uri("/repos/{owner}/{repo}/releases?per_page=1", parts[0], parts[1])
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return parseReleases(json, repoFullName, properties.lookbackHours());
        } catch (Exception e) {
            log.warn("GitHub Releases fetch failed for {}: {}", repoFullName, e.getMessage());
            return List.of();
        }
    }

    List<SoftwareRelease> parseReleases(String json, String repoFullName, int lookbackHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(lookbackHours);
        try {
            JsonNode releases = objectMapper.readTree(json);
            List<SoftwareRelease> result = new ArrayList<>();

            for (JsonNode release : releases) {
                if (release.path("prerelease").asBoolean() || release.path("draft").asBoolean()) {
                    continue;
                }
                String publishedAtRaw = release.path("published_at").asText("");
                if (publishedAtRaw.isBlank()) {
                    continue;
                }
                LocalDateTime releasedAt;
                try {
                    releasedAt = LocalDateTime.parse(publishedAtRaw, DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception e) {
                    continue;
                }
                if (releasedAt.isBefore(cutoff)) {
                    continue;
                }

                String version = release.path("tag_name").asText("");
                String url = release.path("html_url").asText("");
                if (version.isBlank() || url.isBlank()) {
                    continue;
                }
                String body = release.path("body").asText("");
                String excerpt = body.length() > 300 ? body.substring(0, 297) + "..." : body;

                result.add(new SoftwareRelease(repoFullName, version, excerpt, url, releasedAt));
            }
            return result;
        } catch (Exception e) {
            log.warn("GitHub Releases parse failed for {}: {}", repoFullName, e.getMessage());
            return List.of();
        }
    }
}
