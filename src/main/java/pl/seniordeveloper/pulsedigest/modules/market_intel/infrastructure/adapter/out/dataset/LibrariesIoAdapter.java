package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PackageTrend;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches trending libraries and packages from Libraries.io.
 * Degrades gracefully when no API key is configured.
 */
@Slf4j
@Service
public class LibrariesIoAdapter {

    private final ReportProperties.LibrariesIoProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public LibrariesIoAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.librariesIo(), objectMapper);
    }

    LibrariesIoAdapter(ReportProperties.LibrariesIoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<PackageTrend> fetchPackageTrends() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("Libraries.io: no API key configured, skipping fetch");
            return List.of();
        }
        try {
            String json = restClient.get()
                    .uri(uri -> uri
                            .queryParam("api_key", properties.apiKey())
                            .queryParam("platforms", String.join(",", properties.platforms()))
                            .queryParam("sort", "rank")
                            .queryParam("per_page", properties.limit())
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<PackageTrend> trends = parsePackageTrends(json);
            log.info("Libraries.io: {} package trends (limit={})", trends.size(), properties.limit());
            return trends;
        } catch (Exception e) {
            log.warn("Libraries.io fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<PackageTrend> parsePackageTrends(String json) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<PackageTrend> result = new ArrayList<>();
            for (JsonNode node : arr) {
                String name = node.path("name").asText("");
                String platform = node.path("platform").asText("");
                String description = node.path("description").asText("");
                long stars = node.path("stars").asLong(0);
                int dependents = node.path("dependents_count").asInt(0);
                String url = node.path("homepage").asText("");
                if (url.isBlank()) {
                    url = node.path("repository_url").asText("");
                }
                if (url.isBlank()) {
                    url = "https://libraries.io/" + platform + "/" + name.toLowerCase(Locale.ROOT).replace(' ', '-');
                }
                LocalDateTime latestRelease = parseTimestamp(node.path("latest_release_published_at").asText(""));
                if (name.isBlank()) {
                    continue;
                }
                result.add(new PackageTrend(name, platform, description, stars, dependents, url, latestRelease));
            }
            return result;
        } catch (Exception e) {
            log.warn("Libraries.io parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
