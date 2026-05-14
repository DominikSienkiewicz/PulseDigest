package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.TrendNarrativePort;

import java.util.List;
import java.util.Map;

/**
 * Generuje krótkie narracje (1 zdanie per kategoria) jednym batchowym wywołaniem GPT.
 * W razie awarii LLM — zwraca pustą mapę (graceful degradation; pipeline mailowy nie pada).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptTrendNarrativeAdapter implements TrendNarrativePort {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL = "gpt-4o-mini";
    private static final double TEMPERATURE = 0.3;
    private static final int MAX_TOKENS = 800;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;
    private final ObjectMapper objectMapper;
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
    public Map<String, String> narrateBatch(List<TrendCluster> clusters) {
        if (clusters.isEmpty()) {
            return Map.of();
        }
        try {
            String userPrompt = buildPrompt(clusters);
            String requestBody = buildRequest(userPrompt);
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            String content = extractContent(rawResponse);
            Map<String, String> result = parseNarratives(content);
            log.info("Trend narratives generated for {} of {} clusters", result.size(), clusters.size());
            return result;
        } catch (Exception e) {
            log.warn("Trend narrative LLM call failed, returning empty narratives: {}", e.getMessage());
            return Map.of();
        }
    }

    private String buildPrompt(List<TrendCluster> clusters) {
        StringBuilder sb = new StringBuilder();
        sb.append("Oto kategorie technologiczne, które powtórzyły się w naszym daily digest")
                .append(" w ciągu ostatnich kilku dni. Dla każdej kategorii wygeneruj JEDNO zdanie")
                .append(" po polsku (max 100 znaków) opisujące powtarzający się wątek narracyjny\n")
                .append("(np. 'Trzeci dzień z rzędu CVE w popularnych narzędziach' albo 'JVM stack")
                .append(" przyspiesza — premiery i benchmarki').\n\n")
                .append("Zwróć **wyłącznie** obiekt JSON w formacie {\"narratives\":")
                .append(" {\"<kategoria>\": \"<zdanie>\", ...}}.\n\n");
        for (TrendCluster c : clusters) {
            sb.append("- ").append(c.category())
                    .append(" (×").append(c.occurrences()).append("): ");
            for (int i = 0; i < c.exampleTitles().size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(c.exampleTitles().get(i));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildRequest(String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Jesteś analitykiem trendów technologicznych. Odpowiadasz wyłącznie w JSON."),
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

    private Map<String, String> parseNarratives(String content) throws Exception {
        NarrativesPayload payload = objectMapper.readValue(content, NarrativesPayload.class);
        return payload.narratives() != null ? payload.narratives() : Map.of();
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
    private record NarrativesPayload(Map<String, String> narratives) {
    }
}
