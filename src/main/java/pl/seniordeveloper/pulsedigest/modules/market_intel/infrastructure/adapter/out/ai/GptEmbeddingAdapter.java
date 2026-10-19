package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmbeddingPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.CostCeiling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.LlmUsageMeter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Embeds an edition's items in one request (N21).
 *
 * <p>One call, not one per item: the endpoint takes an array, and a hundred requests per edition
 * would have made this feature cost more than the digest. The response carries an {@code index} per
 * vector, which is sorted on rather than assumed — a reordered response silently mapping stories to
 * the wrong vectors is the kind of bug that would merge unrelated articles.
 *
 * <p>Every failure path returns an empty list. The caller reads that as "no opinion" and leaves the
 * items alone, which is the correct degradation: clustering on partial information would merge the
 * wrong things, and a duplicate costs the reader a line while a wrong merge invents a 🔴.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptEmbeddingAdapter implements EmbeddingPort {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    private final OpenAiProperties openAi;
    private final LlmUsageMeter usageMeter;
    private final CostCeiling costCeiling;
    private final ObjectMapper objectMapper;
    private RestClient openAiClient;

    @Value("${llm.embeddings.model:text-embedding-3-small}")
    private String model;

    @PostConstruct
    void init() {
        this.openAiClient = ExternalRestClients.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + openAi.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!costCeiling.allows("embeddings")) {
            return List.of();
        }
        try {
            String raw = openAiClient.post()
                    .uri("/embeddings")
                    .body(objectMapper.writeValueAsString(Map.of("model", model, "input", texts)))
                    .retrieve()
                    .body(String.class);
            return parse(raw, texts.size());
        } catch (Exception e) {
            log.warn("Embedding call failed — the edition is not clustered: {}", e.getMessage());
            return List.of();
        }
    }

    private List<float[]> parse(String json, int expected) throws JsonProcessingException {
        EmbeddingResponse response = objectMapper.readValue(json, EmbeddingResponse.class);
        if (response.data() == null || response.data().size() != expected) {
            log.warn("Embeddings: expected {} vectors, got {}",
                    expected, response.data() == null ? 0 : response.data().size());
            return List.of();
        }
        if (response.usage() != null) {
            // Embeddings bill prompt tokens only; recording zero completions keeps the per-call-site
            // meter honest rather than inventing a number.
            usageMeter.recordCall("embeddings", model, response.usage().promptTokens(), 0L);
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(Embedding::index))
                .map(embedding -> toVector(embedding.embedding()))
                .toList();
    }

    private static float[] toVector(List<Float> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }
        return vector;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<Embedding> data, Usage usage) {
    }

    /**
     * Binds the vector as a {@code List<Float>} rather than a {@code float[]} on purpose.
     *
     * <p>An array component gives a record reference equality and a useless {@code toString} —
     * the trap behind java:S6218 — and the usual answer is to hand-write {@code equals},
     * {@code hashCode} and {@code toString} over {@link java.util.Arrays}. That would be a dozen
     * lines no caller ever reaches, written to satisfy an analyser rather than a need. Binding to
     * a list removes the array, and with it the surprise; the primitive {@code float[]} the port
     * promises is built once, at the boundary, by {@code toVector}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Embedding(int index, List<Float> embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("prompt_tokens") Long promptTokens) {
    }
}
