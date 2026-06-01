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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandNarratorPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.util.List;
import java.util.Map;

/**
 * Generates the one-sentence interpretation of the tech-demand pulse via GPT.
 * On any failure returns blank (graceful degradation — the email still renders the ranking).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptTechDemandNarrativeAdapter implements TechDemandNarratorPort {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL = "gpt-4o-mini";
    private static final double TEMPERATURE = 0.3;
    private static final int MAX_TOKENS = 200;

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
    public String narrate(TechDemandSignal signal) {
        if (signal == null || signal.isEmpty()) {
            return "";
        }
        try {
            String requestBody = buildRequest(buildPrompt(signal));
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            String content = extractContent(rawResponse);
            String narrative = objectMapper.readValue(content, NarrativePayload.class).narrative();
            return narrative != null ? narrative.trim() : "";
        } catch (Exception e) {
            log.warn("Tech-demand narrative LLM call failed, omitting narrative: {}", e.getMessage());
            return "";
        }
    }

    private String buildPrompt(TechDemandSignal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Odbiorca: Principal Architect (JVM, Python-AI, Cloud-Native). ")
                .append("Poniżej ranking popytu na technologie z miesięcznego wątku HN \"Who is hiring?\" ")
                .append("(% = udział ogłoszeń wymieniających technologię; Δ = zmiana w punktach proc. ")
                .append("vs poprzedni miesiąc).\n\n")
                .append("Ranking (").append(signal.totalPostings()).append(" ogłoszeń):\n");
        appendEntries(sb, signal.entries());
        if (!signal.stackEntries().isEmpty()) {
            sb.append("\nStack odbiorcy:\n");
            appendEntries(sb, signal.stackEntries());
        }
        sb.append("\nNapisz JEDNO zdanie po polsku (max 140 znaków), które interpretuje ten obraz rynku ")
                .append("dla architekta — co dominuje, co rośnie/spada, jak wypada jego stack. Konkretnie, bez ogólników.\n")
                .append("Zwróć wyłącznie JSON: {\"narrative\": \"<zdanie>\"}.");
        return sb.toString();
    }

    private void appendEntries(StringBuilder sb, List<TechDemandEntry> entries) {
        for (TechDemandEntry e : entries) {
            sb.append("- ").append(e.name()).append(": ").append(Math.round(e.share() * 100)).append('%');
            if (e.deltaPp() != null) {
                sb.append(" (Δ ").append(String.format(java.util.Locale.ROOT, "%+.0f", e.deltaPp())).append("pp)");
            }
            sb.append('\n');
        }
    }

    private String buildRequest(String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Jesteś analitykiem rynku pracy IT. Odpowiadasz wyłącznie w JSON."),
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
    private record NarrativePayload(String narrative) {
    }
}
