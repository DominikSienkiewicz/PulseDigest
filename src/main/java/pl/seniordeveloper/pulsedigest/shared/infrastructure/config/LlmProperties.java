package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Per-call-site model settings for the three GPT adapters, and the optional price list used to turn
 * token counts into money.
 *
 * <p>These were four sets of `private static final` constants in three adapters until 2026-10-18 —
 * the only settings in the project that needed a recompile to change, in a system where everything
 * else down to a lookback window is configuration (F11). They are per call site rather than global
 * because the three calls are deliberately different: synthesis runs a large model warm, pre-scoring
 * runs a small one at temperature zero, and the reader-profile distillation is small and rare.
 *
 * <p>{@code pricing} is empty by default and deliberately so: real prices change, and a stale number
 * baked into this repository would be a confident lie. Leave it unset and the run reports tokens
 * only; fill it in and the same meters report cost as well.
 */
@ConfigurationProperties(prefix = "llm")
@Validated
public record LlmProperties(
        @Valid @NotNull Call synthesis,
        @Valid @NotNull Call preScoring,
        @Valid @NotNull Call readerProfile,
        @Valid @DefaultValue Map<String, Price> pricing,
        @Valid @DefaultValue Budget budget
) {

    public LlmProperties {
        pricing = pricing != null ? Map.copyOf(pricing) : Map.of();
    }

    /** The price list entry for a model, or empty when none is configured. */
    public Price priceOf(String model) {
        return pricing.getOrDefault(model, Price.unknown());
    }

    /**
     * The per-run ceiling (N40).
     *
     * <p>Counted in <em>tokens</em> first and dollars second, which is the opposite of how a budget
     * is usually written and is the point: {@code pricing} is deliberately absent from the shipped
     * configuration, so a dollars-only ceiling would be inert in exactly the installation that has
     * it turned on. Tokens are metered unconditionally, so the token ceiling is the one that
     * actually guards anything until someone fills a price list in.
     *
     * <p>Both are per run, not per call: a prompt bug spends its money across many small calls, and
     * a per-call cap (which {@code max-tokens} already is) cannot see that.
     *
     * @param enabled   whether the ceiling is enforced at all
     * @param maxTokens prompt + completion tokens a single run may spend before further calls are
     *                  refused; {@code 0} disables the token ceiling
     * @param maxUsd    dollars a single run may spend before further calls are refused; {@code 0}
     *                  disables the money ceiling, and it is inert anyway without {@code pricing}
     */
    public record Budget(
            @DefaultValue("true") boolean enabled,
            @Min(0) @DefaultValue("0") long maxTokens,
            @DecimalMin("0.0") @DefaultValue("0") double maxUsd
    ) {

        /** Whether anything is actually capped — an enabled budget with no limits caps nothing. */
        public boolean capsAnything() {
            return enabled && (maxTokens > 0 || maxUsd > 0);
        }
    }

    /**
     * One call site's model settings.
     *
     * @param model         the model this call asks for
     * @param fallbackModel the model to retry on when the first one fails; blank means no fallback
     * @param temperature   sampling temperature
     * @param maxTokens     completion ceiling — the cap that produces {@code finish_reason=length}
     */
    public record Call(
            @NotBlank String model,
            @DefaultValue("") String fallbackModel,
            @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
            @Min(1) int maxTokens
    ) {

        /** Whether this call has somewhere to fall back to. */
        public boolean hasFallback() {
            return fallbackModel != null && !fallbackModel.isBlank();
        }
    }

    /** Dollars per million tokens, as the provider bills them. */
    public record Price(
            @DecimalMin("0.0") @DefaultValue("0") double inputPerMillionTokens,
            @DecimalMin("0.0") @DefaultValue("0") double outputPerMillionTokens
    ) {

        static Price unknown() {
            return new Price(0.0, 0.0);
        }

        /** Whether a price was actually configured, as opposed to defaulted to zero. */
        public boolean isKnown() {
            return inputPerMillionTokens > 0 || outputPerMillionTokens > 0;
        }

        /** Cost in dollars of the given token counts at this price. */
        public double costOf(long promptTokens, long completionTokens) {
            return promptTokens / 1_000_000.0 * inputPerMillionTokens
                    + completionTokens / 1_000_000.0 * outputPerMillionTokens;
        }
    }
}
