package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PreScoringPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.CostCeiling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.LlmUsageMeter;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triages prompt candidates with gpt-4o-mini so the main gpt-4o call reads a denser payload.
 *
 * <p>Every failure path — no API key, HTTP error, malformed JSON — returns an empty map, which the
 * caller reads as "no opinion" and passes the full payload through. A cost optimisation must never
 * be able to shrink the digest.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptPreScoringAdapter implements PreScoringPort {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final int TITLE_CLIP = 110;

    private final OpenAiProperties openAi;
    private final LlmProperties llm;
    private final LlmUsageMeter usageMeter;
    private final CostCeiling costCeiling;
    private final ObjectMapper objectMapper;
    private final InterestProfileProperties interestProfile;
    private RestClient openAiClient;

    @PostConstruct
    void init() {
        this.openAiClient = ExternalRestClients.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + openAi.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Map<String, Integer> score(List<PreScoringCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        // N40. Skipping triage costs the run accuracy, not the edition: the full payload simply
        // goes to the main model, which is the documented fallback for a failed pre-scoring call.
        if (!costCeiling.allows("pre-scoring")) {
            return Map.of();
        }
        try {
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(buildRequest(buildPrompt(candidates)))
                    .retrieve()
                    .body(String.class);
            Map<String, Integer> scores = parseScores(extractContent(rawResponse), candidates);
            log.info("Pre-scoring: {} of {} candidates rated by {}",
                    scores.size(), candidates.size(), llm.preScoring().model());
            return scores;
        } catch (Exception e) {
            log.warn("Pre-scoring call failed, sending the full payload to the main model: {}", e.getMessage());
            return Map.of();
        }
    }

    // Indices, not URLs: the model echoes a short integer per item instead of a long URL, which is
    // both cheaper and immune to the URL mangling that makes echoed identifiers unreliable.
    private String buildPrompt(List<PreScoringCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Odbiorca: ").append(interestProfile.persona()).append("\n\n")
                .append("Oceń każdy nagłówek w skali 0-10: jak bardzo jest istotny dla tego odbiorcy.\n")
                .append("0 = zupełnie nieistotne, 10 = musi to przeczytać. Oceniaj po tytule i źródle.\n")
                .append("Zwróć wyłącznie JSON: {\"scores\": [{\"i\": 0, \"s\": 7}, ...]} — jeden wpis per item.\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            PreScoringCandidate c = candidates.get(i);
            String title = c.title() == null ? "" : c.title();
            sb.append(i).append(". [").append(c.source()).append("] ")
                    .append(title.substring(0, Math.min(TITLE_CLIP, title.length())))
                    .append(" (engagement=").append(c.engagement()).append(")\n");
        }
        return sb.toString();
    }

    private Map<String, Integer> parseScores(String content, List<PreScoringCandidate> candidates) throws JsonProcessingException {
        ScorePayload payload = objectMapper.readValue(content, ScorePayload.class);
        if (payload.scores() == null) {
            return Map.of();
        }
        Map<String, Integer> byUrl = new HashMap<>();
        for (ScoreEntry entry : payload.scores()) {
            if (entry.i() != null && entry.s() != null && entry.i() >= 0 && entry.i() < candidates.size()) {
                byUrl.put(candidates.get(entry.i()).url(), Math.clamp(entry.s(), 0, 10));
            }
        }
        return Map.copyOf(byUrl);
    }

    private String buildRequest(String userPrompt) throws JsonProcessingException {
        Map<String, Object> request = Map.of(
                "model", llm.preScoring().model(),
                "temperature", llm.preScoring().temperature(),
                "max_tokens", llm.preScoring().maxTokens(),
                // N22. Same strict schema treatment as synthesis; here the shape is two integers,
                // and a malformed triage response silently degraded to "no opinion".
                "response_format", LlmResponseSchemas.responseFormat(
                        "pulsedigest_prescoring", LlmResponseSchemas.preScoringSchema()),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Jesteś triage'em nagłówków technologicznych. Odpowiadasz wyłącznie w JSON."),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        return objectMapper.writeValueAsString(request);
    }

    private String extractContent(String json) throws JsonProcessingException {
        OpenAiResponse response = objectMapper.readValue(json, OpenAiResponse.class);
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI returned empty choices[]");
        }
        meter(response.usage());
        return response.choices().getFirst().message().content();
    }

    /** Attributes this call's tokens before the content is handed on. */
    private void meter(Usage usage) {
        if (usage != null) {
            usageMeter.recordCall("pre-scoring", llm.preScoring().model(), usage.promptTokens(), usage.completionTokens());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") Long promptTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("completion_tokens") Long completionTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScorePayload(List<ScoreEntry> scores) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScoreEntry(Integer i, Integer s) {
    }
}
