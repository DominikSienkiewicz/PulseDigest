package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfileDistillerPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.CostCeiling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.LlmUsageMeter;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Distils accumulated votes into a few dated, evidenced hypotheses about the reader, once a week,
 * with gpt-4o-mini.
 *
 * <p>The prompt forbids a hypothesis without evidence and caps their number. Both constraints exist
 * because the failure mode of this feature is a model that sounds insightful about a reader it has
 * five clicks' worth of data on. Hypotheses missing an evidence line are dropped here, not trusted.
 *
 * <p>Any failure returns {@link Optional#empty()}: the previously stored profile survives.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GptReaderProfileDistiller implements ReaderProfileDistillerPort {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final int MAX_HYPOTHESES = 5;

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
    public Optional<ReaderProfile> distil(ProfileEvidence evidence) {
        if (evidence == null || evidence.totalVotes() == 0) {
            return Optional.empty();
        }
        // N40. The previous profile survives a skipped distillation — it is re-distilled on a
        // schedule, not per run, so a run that skips it is not a run without a reader model.
        if (!costCeiling.allows("reader-profile")) {
            return Optional.empty();
        }
        try {
            String raw = openAiClient.post()
                    .uri("/chat/completions")
                    .body(buildRequest(buildPrompt(evidence)))
                    .retrieve()
                    .body(String.class);
            List<ProfileHypothesis> hypotheses = evidenced(parse(extractContent(raw)));
            if (hypotheses.isEmpty()) {
                log.warn("Reader profile distillation returned no evidenced hypothesis — keeping the old profile");
                return Optional.empty();
            }
            return Optional.of(new ReaderProfile(Instant.now(), evidence.totalVotes(), hypotheses));
        } catch (Exception e) {
            log.warn("Reader profile distillation failed, keeping the stored profile: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** A claim without evidence is a guess. Drop it rather than let it steer the digest. */
    private static List<ProfileHypothesis> evidenced(List<ProfileHypothesis> hypotheses) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return hypotheses.stream()
                .filter(h -> h.statement() != null && !h.statement().isBlank())
                .filter(h -> h.evidence() != null && !h.evidence().isBlank())
                .limit(MAX_HYPOTHESES)
                .map(h -> new ProfileHypothesis(h.statement(), h.evidence(),
                        h.observedAt() != null ? h.observedAt() : today))
                .toList();
    }

    private String buildPrompt(ProfileEvidence evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bazowa persona odbiorcy: ").append(interestProfile.persona()).append("\n\n")
                .append("Poniżej jego realne głosy 👍/👎 z ostatnich tygodni (wartość = netto UP−DOWN).\n\n")
                .append("Kategorie:\n");
        appendVotes(sb, evidence.netVotesByCategory());
        sb.append("\nŹródła:\n");
        appendVotes(sb, evidence.netVotesBySource());
        if (!evidence.dislikedTitles().isEmpty()) {
            sb.append("\nNagłówki, które odrzucił:\n");
            evidence.dislikedTitles().forEach(t -> sb.append("- ").append(t).append('\n'));
        }
        sb.append("\nSformułuj maksymalnie ").append(MAX_HYPOTHESES)
                .append(" hipotez o tym, czego ten czytelnik chce więcej, a czego mniej — PONAD to,\n")
                .append("co mówi już bazowa persona. Każda hipoteza MUSI cytować konkretne liczby z powyższych\n")
                .append("danych. Jeśli dane są zbyt skąpe na jakąkolwiek hipotezę, zwróć pustą listę.\n")
                .append("Nie zgaduj, nie powtarzaj persony, nie uogólniaj.\n")
                .append("Zwróć wyłącznie JSON: {\"hypotheses\":[{\"statement\":\"...\",\"evidence\":\"...\"}]}");
        return sb.toString();
    }

    private static void appendVotes(StringBuilder sb, Map<String, Integer> votes) {
        if (votes.isEmpty()) {
            sb.append("(brak)\n");
            return;
        }
        votes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("- ").append(e.getKey()).append(": ")
                        .append(e.getValue() > 0 ? "+" : "").append(e.getValue()).append('\n'));
    }

    private String buildRequest(String userPrompt) throws Exception {
        Map<String, Object> request = Map.of(
                "model", llm.readerProfile().model(),
                "temperature", llm.readerProfile().temperature(),
                "max_tokens", llm.readerProfile().maxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Destylujesz profil czytelnika z jego głosów. Odpowiadasz wyłącznie w JSON."),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        return objectMapper.writeValueAsString(request);
    }

    private List<ProfileHypothesis> parse(String content) throws Exception {
        HypothesesPayload payload = objectMapper.readValue(content, HypothesesPayload.class);
        return payload.hypotheses() != null ? payload.hypotheses() : List.of();
    }

    private String extractContent(String json) throws Exception {
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
            usageMeter.record("reader-profile", llm.readerProfile().model(), usage.promptTokens(), usage.completionTokens());
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
    private record HypothesesPayload(List<ProfileHypothesis> hypotheses) {
    }
}
