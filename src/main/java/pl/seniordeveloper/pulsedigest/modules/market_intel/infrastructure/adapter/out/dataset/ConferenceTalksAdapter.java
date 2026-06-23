package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.QuotaErrors;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ConferenceTalksProperties;

import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

/**
 * Fetches recently published conference talk recordings via the YouTube Data API v3 search endpoint.
 * Degrades gracefully when no API key is configured.
 */
@Slf4j
@Service
public class ConferenceTalksAdapter {

    private final ConferenceTalksProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public ConferenceTalksAdapter(ConferenceTalksProperties properties, ObjectMapper objectMapper) {
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

    public List<ConferenceTalk> fetchConferenceTalks() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("Conference Talks: no YouTube API key configured, skipping fetch");
            return List.of();
        }
        List<ConferenceTalk> allTalks = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        for (var channel : properties.channels()) {
            try {
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
                allTalks.addAll(parseSearchResults(json, channel.channelName(), channel.conferenceName()));
            } catch (Exception e) {
                QuotaErrors.rethrowIfQuota(e);
                failed++;
                lastError = e;
                log.warn("Conference Talks fetch failed for {}: {}", channel.conferenceName(), e.getMessage());
            }
        }
        if (failed > 0 && failed == properties.channels().size()) {
            throw new IllegalStateException(
                    "All " + failed + " YouTube channels failed; last error: "
                            + (lastError != null ? lastError.getMessage() : "unknown"),
                    lastError);
        }
        log.info("Conference Talks: {} talks from {} channels ({} udanych)",
                allTalks.size(), properties.channels().size(), properties.channels().size() - failed);
        return allTalks;
    }

    List<ConferenceTalk> parseSearchResults(String json, String channelName, String conferenceName) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.lookbackDays());
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            List<ConferenceTalk> result = new ArrayList<>();
            for (JsonNode item : items) {
                parseItem(item, channelName, conferenceName, cutoff).ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("Conference Talks parse failed for {}: {}", conferenceName, e.getMessage());
            return List.of();
        }
    }

    private Optional<ConferenceTalk> parseItem(
            JsonNode item, String channelName, String conferenceName, LocalDateTime cutoff) {
        JsonNode snippet = item.path("snippet");
        String title = snippet.path("title").asText("");
        String publishedStr = snippet.path("publishedAt").asText("");
        LocalDateTime publishedAt = parseTimestamp(publishedStr);
        if (publishedAt == null || publishedAt.isBefore(cutoff)) {
            return Optional.empty();
        }
        JsonNode idNode = item.path("id");
        String videoId = idNode.path("videoId").asText("");
        String url = videoId.isBlank() ? "" : "https://www.youtube.com/watch?v=" + videoId;
        if (title.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ConferenceTalk(title, channelName, conferenceName, url, publishedAt, 0));
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception _) {
            return null;
        }
    }
}
