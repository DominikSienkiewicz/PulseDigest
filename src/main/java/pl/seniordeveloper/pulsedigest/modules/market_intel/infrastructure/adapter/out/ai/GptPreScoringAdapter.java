package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PreScoringCandidate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PreScoringPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
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
    private static final String MODEL = "gpt-4o-mini";
    private static final double TEMPERATURE = 0.0;
    private static final int MAX_TOKENS = 4000;
    private static final int TITLE_CLIP = 110;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;
    private final ObjectMapper objectMapper;
    private final InterestProfileProperties interestProfile;
    private RestClient openAiClient;

    @PostConstruct
    void init() {
        this.openAiClient = ExternalRestClients.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Map<String, Integer> score(List<PreScoringCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        try {
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(buildRequest(buildPrompt(candidates)))
                    .retrieve()
                    .body(String.class);
            Map<String, Integer> scores = parseScores(extractContent(rawResponse), candidates);
            log.info("Pre-scoring: {} of {} candidates rated by {}", scores.size(), candidates.size(), MODEL);
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

    private Map<String, Integer> parseScores(String content, List<PreScoringCandidate> candidates) throws Exception {
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

    private String buildRequest(String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Jesteś triage'em nagłówków technologicznych. Odpowiadasz wyłącznie w JSON."),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        return objectMapper.writeValueAsString(request);
    }

    private String extractContent(String json) throws Exception {
        OpenAiResponse response = objectMapper.readValue(json, OpenAiResponse.class);
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI returned empty choices[]");
        }
        return response.choices().getFirst().message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices) {
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
