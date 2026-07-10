package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptItemMeta;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaSignals;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Kroki 3–5 pipeline'u raportowego: Synteza → Personalizacja → Formatowanie.
 *
 * <p>Jeden przejściowy błąd OpenAI po kilkunastu (często płatnych) fetchach oznaczałby stracony
 * digest, więc synteza broni się trójstopniowo: retry z backoffem, ponowienie z mniejszą liczbą
 * itemów przy ucięciu odpowiedzi na token capie, a na końcu awaryjny model zapasowy. Wyczerpana
 * kwota jest jedynym błędem propagowanym natychmiast — powtórka spaliłaby czas bez szansy na sukces.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptSynthesisAdapter implements LlmSynthesisPort {
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String MODEL = "gpt-4o";
    private static final String FALLBACK_MODEL = "gpt-4o-mini";
    private static final String EMERGENCY_MARKER = "⚠️ Digest awaryjny (model zapasowy) — ";
    private static final double TEMPERATURE = 0.25;
    private static final int MAX_TOKENS = 10000;
    private static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Duration LLM_READ_TIMEOUT = Duration.ofSeconds(180);

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;
    private final ObjectMapper objectMapper;
    private final ReportPromptBuilder promptBuilder;
    private RestClient openAiClient;

    @PostConstruct
    void init() {
        this.openAiClient = ExternalRestClients.builder(LLM_READ_TIMEOUT)
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
        log.debug("System prompt length: {} znaków", systemPrompt.length());

        try {
            return synthesizeWith(MODEL, systemPrompt, research);
        } catch (LlmQuotaException e) {
            throw e;
        } catch (LlmSynthesisException e) {
            log.warn("Model {} zawiódł ({}) — awaryjna synteza na {}", MODEL, e.getMessage(), FALLBACK_MODEL);
            return markAsEmergency(synthesizeWith(FALLBACK_MODEL, systemPrompt, research));
        }
    }

    /**
     * Full-intake synthesis with retries; on a truncated response the same model is given one more
     * chance with half the items — the only recovery that can actually change the outcome.
     */
    private ReportData synthesizeWith(String model, String systemPrompt, ResearchResult research) {
        try {
            return callWithRetry(model, systemPrompt, () -> promptBuilder.buildPrompt(research));
        } catch (LlmTruncatedException e) {
            int reducedCap = PromptItemSelector.TOTAL_CAP / 2;
            log.warn("Odpowiedź {} ucięta na token capie — ponawiam z {} itemami", model, reducedCap);
            return callWithRetry(model, systemPrompt, () -> promptBuilder.buildPrompt(research, reducedCap));
        }
    }

    private ReportData callWithRetry(String model, String systemPrompt, Supplier<PromptPayload> payload) {
        LlmSynthesisException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call(model, systemPrompt, payload.get());
            } catch (LlmQuotaException | LlmTruncatedException e) {
                throw e;
            } catch (LlmSynthesisException e) {
                lastFailure = e;
                log.warn("Próba {}/{} wywołania {} nieudana: {}", attempt, MAX_ATTEMPTS, model, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw lastFailure;
    }

    private ReportData call(String model, String systemPrompt, PromptPayload payload) {
        log.debug("User prompt length: {} znaków", payload.userPrompt().length());
        try {
            String requestBody = buildOpenAiRequest(model, systemPrompt, payload.userPrompt());
            String rawResponse = openAiClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            OpenAiResponse response = objectMapper.readValue(rawResponse, OpenAiResponse.class);
            logUsage(model, response.usage());
            String jsonContent = extractContent(response);
            ReportData report = rejoinMetadata(
                    objectMapper.readValue(jsonContent, ReportData.class), payload.inputMeta());
            log.info("Digest wygenerowany przez {} | {} insights | {} itemów", model,
                    report.topInsights() != null ? report.topInsights().size() : 0,
                    report.items() != null ? report.items().size() : 0);
            return report;

        } catch (LlmTruncatedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Błąd podczas wywołania OpenAI API: {}", e.getMessage(), e);
            if (QuotaSignals.matches(rootMessage(e))) {
                throw new LlmQuotaException("OpenAI synthesis failed (quota exhausted): " + e.getMessage(), e);
            }
            throw new LlmSynthesisException("OpenAI synthesis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Restores the input-side truth for every returned item and drops the ones the model invented.
     * Counters are dumped to the CI log on shutdown ({@code MetricsLogger}), so a rising
     * {@code llm.output.rejected} is the signal that the model — or someone's injected text — started
     * fabricating URLs.
     */
    private static ReportData rejoinMetadata(ReportData report, Map<String, PromptItemMeta> inputMeta) {
        int returned = report.items() != null ? report.items().size() : 0;
        ReportData rejoined = report.withRejoinedMetadata(inputMeta);
        int kept = rejoined.items() != null ? rejoined.items().size() : 0;
        int rejected = returned - kept;
        if (rejected > 0) {
            log.warn("Odrzucono {} z {} itemów — URL spoza promptu (halucynacja lub prompt injection)",
                    rejected, returned);
            Metrics.counter("llm.output.rejected").increment(rejected);
        }
        Metrics.counter("llm.output.rejoined").increment(kept);
        return rejoined;
    }

    /**
     * Prefixes the editorial lead so the reader knows this edition came from the fallback model and
     * is therefore shallower than usual — a silent downgrade would erode trust in the digest.
     */
    private static ReportData markAsEmergency(ReportData report) {
        String editorial = report.editorial() == null ? "" : report.editorial();
        return new ReportData(report.emailPreview(), EMERGENCY_MARKER + editorial,
                report.topInsights(), report.items(), report.signals());
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF.multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmSynthesisException("Interrupted during OpenAI retry backoff", e);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    // -------------------------------------------------------------------------
    // OpenAI API helpers
    // -------------------------------------------------------------------------

    private String buildOpenAiRequest(String model, String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", model,
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

    private String extractContent(OpenAiResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new LlmSynthesisException("OpenAI returned empty choices[]");
        }
        Choice choice = response.choices().getFirst();
        if ("length".equals(choice.finishReason())) {
            // The model hit max_tokens mid-output: json_object mode does NOT guarantee well-formed
            // JSON on truncation, so fail fast with an actionable message instead of a cryptic parse error.
            throw new LlmTruncatedException(
                    "OpenAI response truncated at the token cap (finish_reason=length) — "
                    + "increase max_tokens or reduce the number of items sent for scoring");
        }
        return choice.message().content();
    }

    private void logUsage(String model, Usage usage) {
        if (usage == null) {
            return;
        }
        log.info("OpenAI usage ({}): prompt={} completion={} total={} tokens (max_tokens cap={})",
                model, usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), MAX_TOKENS);
    }

    // -------------------------------------------------------------------------
    // Internal JSON mapping records (OpenAI response shape)
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
