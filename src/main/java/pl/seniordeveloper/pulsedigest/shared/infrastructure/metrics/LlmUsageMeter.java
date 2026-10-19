package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;

/**
 * Attributes token spend — and, when a price list is configured, money — to the call site and model
 * that incurred it.
 *
 * <p>Before this, the only LLM number the run produced was one INFO line from the synthesis adapter;
 * the pre-scoring and reader-profile calls parsed the response and threw the {@code usage} block
 * away. Nothing anywhere said how much a run cost, which made "is pre-scoring paying for itself?"
 * and "did October get more expensive?" unanswerable (F11).
 *
 * <p>Counters go through Micrometer, so {@code MetricsLogger} already dumps them on shutdown and
 * every run self-reports its spend in the Actions log without any new plumbing.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LlmUsageMeter {

    private static final String TAG_CALL = "call";
    private static final String TAG_MODEL = "model";

    private final LlmProperties llm;

    /**
     * Records one completed call. A null or absent usage block is normal — some error paths return
     * before the provider reports usage — and is simply not counted rather than counted as zero.
     */
    public void recordCall(String callSite, String model, Number promptTokens, Number completionTokens) {
        if (model == null || model.isBlank() || promptTokens == null || completionTokens == null) {
            return;
        }
        long prompt = promptTokens.longValue();
        long completion = completionTokens.longValue();
        Metrics.counter("llm.tokens", TAG_CALL, callSite, TAG_MODEL, model, "kind", "prompt")
                .increment((double) prompt);
        Metrics.counter("llm.tokens", TAG_CALL, callSite, TAG_MODEL, model, "kind", "completion")
                .increment((double) completion);
        Metrics.counter("llm.calls", TAG_CALL, callSite, TAG_MODEL, model).increment();

        LlmProperties.Price price = llm.priceOf(model);
        if (!price.isKnown()) {
            log.info("llm-usage call={} model={} prompt={} completion={} cost=unpriced",
                    callSite, model, prompt, completion);
            return;
        }
        double cost = price.costOf(prompt, completion);
        Metrics.counter("llm.cost.usd", TAG_CALL, callSite, TAG_MODEL, model).increment(cost);
        log.info("llm-usage call={} model={} prompt={} completion={} cost=${}",
                callSite, model, prompt, completion, String.format(java.util.Locale.ROOT, "%.4f", cost));
    }
}
