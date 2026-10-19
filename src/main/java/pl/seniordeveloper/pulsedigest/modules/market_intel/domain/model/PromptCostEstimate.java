package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * What a prompt replay is about to cost, stated before the call rather than after it (N25).
 *
 * <p>Replaying the prompt spends real money every time, which is why it sits behind its own flag and
 * why the run says the number first. {@code priced} is false when no price list is configured — then
 * the token count is all that can honestly be reported, and claiming a dollar figure would be
 * invention.
 */
public record PromptCostEstimate(long estimatedPromptTokens, double estimatedCostUsd, boolean priced) {

    public static PromptCostEstimate unpriced(long tokens) {
        return new PromptCostEstimate(tokens, 0.0, false);
    }

    /** One line for the operator who is about to spend this. */
    @JsonIgnore
    public String describe() {
        return priced
                ? "~%d prompt tokens, ~$%.4f".formatted(estimatedPromptTokens, estimatedCostUsd)
                : "~%d prompt tokens, cost unpriced (set llm.pricing to see dollars)"
                        .formatted(estimatedPromptTokens);
    }
}
