package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechnologyRadarProperties;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches the latest Thoughtworks Technology Radar entries.
 * The radar is published quarterly; this adapter refetches each run.
 */
@Slf4j
@Service
public class TechnologyRadarAdapter {

    private final TechnologyRadarProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public TechnologyRadarAdapter(TechnologyRadarProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<RadarEntry> fetchTechRadarEntries() {
        try {
            String json = restClient.get()
                    .uri(URI.create(properties.dataPath()))
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<RadarEntry> entries = parseRadarEntries(json);
            log.info("Tech Radar: {} entries", entries.size());
            return entries;
        } catch (Exception e) {
            log.warn("Tech Radar fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<RadarEntry> parseRadarEntries(String json) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<RadarEntry> result = new ArrayList<>();
            for (JsonNode item : arr) {
                String name = item.path("name").asText("");
                String ring = capitalize(item.path("ring").asText(""));
                String quadrant = capitalize(item.path("quadrant").asText(""));
                String description = item.path("description").asText("");
                String url = item.path("url").asText("");
                if (url.isBlank()) {
                    url = "https://www.thoughtworks.com/radar";
                }
                if (name.isBlank()) {
                    continue;
                }
                result.add(new RadarEntry(name, ring, quadrant, description, url, LocalDateTime.now()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Tech Radar parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
