package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches recently published conference talk recordings via the YouTube Data API v3 search endpoint.
 * Degrades gracefully when no API key is configured.
 */
@Slf4j
@Service
public class ConferenceTalksAdapter {

    private final ReportProperties.ConferenceTalksProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public ConferenceTalksAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.conferenceTalks(), objectMapper);
    }

    ConferenceTalksAdapter(ReportProperties.ConferenceTalksProperties properties, ObjectMapper objectMapper) {
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

    public List<ConferenceTalk> fetchConferenceTalks() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("Conference Talks: no YouTube API key configured, skipping fetch");
            return List.of();
        }
        try {
            List<ConferenceTalk> allTalks = new ArrayList<>();
            for (var channel : properties.channels()) {
                String json = restClient.get()
                        .uri(uri -> uri
                                .queryParam("part", "snippet")
                                .queryParam("channelId", channel.channelId())
                                .queryParam("order", "date")
                                .queryParam("maxResults", properties.maxResults())
                                .queryParam("type", "video")
                                .queryParam("key", properties.apiKey())
                                .build())
                        .retrieve()
                        .body(String.class);
                if (json == null || json.isBlank()) {
                    continue;
                }
                List<ConferenceTalk> talks = parseSearchResults(json, channel.channelName(),
                        channel.conferenceName());
                allTalks.addAll(talks);
            }
            log.info("Conference Talks: {} talks from {} channels", allTalks.size(), properties.channels().size());
            return allTalks;
        } catch (Exception e) {
            log.warn("Conference Talks fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<ConferenceTalk> parseSearchResults(String json, String channelName, String conferenceName) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.lookbackDays());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            List<ConferenceTalk> result = new ArrayList<>();
            for (JsonNode item : items) {
                JsonNode snippet = item.path("snippet");
                String title = snippet.path("title").asText("");
                String publishedStr = snippet.path("publishedAt").asText("");
                LocalDateTime publishedAt = parseTimestamp(publishedStr);
                if (publishedAt == null || publishedAt.isBefore(cutoff)) {
                    continue;
                }
                JsonNode idNode = item.path("id");
                String videoId = idNode.path("videoId").asText("");
                String url = videoId.isBlank() ? "" : "https://www.youtube.com/watch?v=" + videoId;
                if (title.isBlank()) {
                    continue;
                }
                result.add(new ConferenceTalk(title, channelName, conferenceName, url, publishedAt, 0));
            }
            return result;
        } catch (Exception e) {
            log.warn("Conference Talks parse failed for {}: {}", conferenceName, e.getMessage());
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
