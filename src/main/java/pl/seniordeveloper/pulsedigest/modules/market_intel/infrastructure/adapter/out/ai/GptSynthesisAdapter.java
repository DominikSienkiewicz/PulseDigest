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
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;

import java.util.List;
import java.util.Map;

/**
 * Kroki 3–5 pipeline'u raportowego: Synteza → Personalizacja → Formatowanie.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptSynthesisAdapter implements LlmSynthesisPort {
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL = "gpt-4o";
    private static final double TEMPERATURE = 0.25;
    private static final int MAX_TOKENS = 10000;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;
    private final ObjectMapper objectMapper;
    private final ReportPromptBuilder promptBuilder;
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
    public ReportData synthesize(ResearchResult research) {
        log.info("=== Krok 3–5: Synteza, personalizacja i formatowanie przez GPT-4o (JSON mode) ===");
        log.info("Dane wejściowe: {} tweetów, {} HN, {} GH repos",
                research.tweets().size(), research.hackerNewsPosts().size(), research.githubRepos().size());

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(research);

        log.debug("System prompt length: {} znaków", systemPrompt.length());
        log.debug("User prompt length: {} znaków", userPrompt.length());

        try {
            String requestBody = buildOpenAiRequest(systemPrompt, userPrompt);
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String jsonContent = extractContent(rawResponse);
            ReportData report = objectMapper.readValue(jsonContent, ReportData.class);
            log.info("Digest wygenerowany | {} insights | {} itemów",
                    report.topInsights() != null ? report.topInsights().size() : 0,
                    report.items() != null ? report.items().size() : 0);
            return report;

        } catch (Exception e) {
            log.error("Błąd podczas wywołania OpenAI API: {}", e.getMessage(), e);
            throw new LlmSynthesisException("OpenAI synthesis failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // OpenAI API helpers
    // -------------------------------------------------------------------------

    private String buildOpenAiRequest(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        return objectMapper.writeValueAsString(request);
    }

    private String extractContent(String json) throws Exception {
        OpenAiResponse response = objectMapper.readValue(json, OpenAiResponse.class);
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new LlmSynthesisException("OpenAI returned empty choices[]");
        }
        return response.choices().getFirst().message().content();
    }

    // -------------------------------------------------------------------------
    // Internal JSON mapping records (OpenAI response shape)
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }
}
