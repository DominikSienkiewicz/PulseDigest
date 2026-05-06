package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches trending AI models from the public Hugging Face Hub API.
 */
@Slf4j
@Service
public class HuggingFaceTrendingAdapter {

    private final ReportProperties.HuggingFaceProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public HuggingFaceTrendingAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.huggingFace(), objectMapper);
    }

    HuggingFaceTrendingAdapter(ReportProperties.HuggingFaceProperties properties, ObjectMapper objectMapper) {
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

    public List<HuggingFaceModel> fetchTrendingModels() {
        try {
            String json = restClient.get()
                    .uri(uri -> uri
                            .queryParam("sort", "trendingScore")
                            .queryParam("direction", "-1")
                            .queryParam("limit", properties.limit())
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<HuggingFaceModel> models = parseModels(json);
            log.info("Hugging Face: {} trending models (limit={})", models.size(), properties.limit());
            return models;
        } catch (Exception e) {
            log.warn("Hugging Face fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<HuggingFaceModel> parseModels(String json) {
        Set<String> relevantPipelines = new HashSet<>(properties.relevantPipelines());
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<HuggingFaceModel> result = new ArrayList<>();
            for (JsonNode node : arr) {
                String id = node.path("modelId").asText(node.path("id").asText(""));
                if (id.isBlank()) {
                    continue;
                }
                String pipelineTag = node.path("pipeline_tag").asText("");
                if (!relevantPipelines.isEmpty() && !relevantPipelines.contains(pipelineTag)) {
                    continue;
                }
                long downloads = node.path("downloads").asLong(0);
                long likes = node.path("likes").asLong(0);
                if (likes < properties.minLikes() && downloads < properties.minDownloads()) {
                    continue;
                }
                LocalDateTime lastModified = parseTimestamp(node.path("lastModified").asText(""));
                String author = id.contains("/") ? id.substring(0, id.indexOf('/')) : "";
                String url = "https://huggingface.co/" + id;
                result.add(new HuggingFaceModel(id, author, downloads, likes, lastModified, pipelineTag, url));
            }
            return result;
        } catch (Exception e) {
            log.warn("Hugging Face parse failed: {}", e.getMessage());
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
