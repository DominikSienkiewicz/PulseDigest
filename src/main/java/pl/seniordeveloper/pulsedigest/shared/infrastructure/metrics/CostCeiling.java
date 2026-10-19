package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;

import java.util.Collection;
import java.util.List;

/**
 * The per-run spend ceiling (N40).
 *
 * <p>Nothing bounded a run's spend except {@code max-tokens} per call, which is a cap on one
 * response and blind to a run that makes many. A prompt bug or an intake spike spends real money
 * twice a week, unattended, and the first evidence is the invoice.
 *
 * <p>This reads the counters {@link LlmUsageMeter} already writes, so it needs no new plumbing and
 * cannot disagree with what the run reports about itself.
 *
 * <h2>What it does and does not promise</h2>
 * It refuses the <em>next</em> call once the ceiling is crossed. Usage is reported by the provider
 * after a call completes, so a run can overshoot by at most one call — a smaller and more honest
 * guarantee than pre-estimating every prompt, and the one the meters can actually support.
 *
 * <p>Optional calls degrade: pre-scoring and the reader-profile distillation are skipped and the run
 * continues without them, because aborting an almost-complete edition wastes everything already
 * spent. The synthesis call has nothing to degrade to, so crossing the ceiling there fails the run —
 * which reaches the author as the same failure alert any other broken run does.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CostCeiling {

    private static final double WARN_AT = 0.8;

    private final LlmProperties llm;

    /**
     * The same registry {@code MetricsConfig} attaches to the global composite, injected rather than
     * reached for statically. {@link LlmUsageMeter} writes through {@code Metrics.counter(...)}, which
     * forwards to it, so both see one set of numbers — and a guard that reads an injected registry
     * can be tested in isolation, which one reading a JVM-wide static cannot.
     */
    private final MeterRegistry registry;

    /**
     * Whether an optional call may proceed. Logs the refusal rather than returning quietly — a
     * degraded edition that never says it was degraded is indistinguishable from a cheap week.
     */
    public boolean allows(String callSite) {
        LlmProperties.Budget budget = llm.budget();
        if (!budget.capsAnything()) {
            return true;
        }
        long tokens = tokensSpent();
        double usd = usdSpent();
        if (overTokens(budget, tokens) || overUsd(budget, usd)) {
            log.warn("budget-ceiling skipping optional call={} spent_tokens={} spent_usd={} "
                            + "max_tokens={} max_usd={}",
                    callSite, tokens, String.format(java.util.Locale.ROOT, "%.4f", usd), budget.maxTokens(), budget.maxUsd());
            return false;
        }
        warnIfClose(callSite, budget, tokens, usd);
        return true;
    }

    /**
     * Refuses a call the run cannot do without. The message names both numbers, because the one that
     * tripped is the one worth raising — and a budget alert that does not say what was spent is a
     * puzzle rather than a warning.
     */
    public void requireHeadroom(String callSite) {
        LlmProperties.Budget budget = llm.budget();
        if (!budget.capsAnything()) {
            return;
        }
        long tokens = tokensSpent();
        double usd = usdSpent();
        if (overTokens(budget, tokens) || overUsd(budget, usd)) {
            throw new BudgetExceededException(
                    "Per-run budget spent before %s: %d tokens (max %d), $%.4f (max $%.2f)"
                            .formatted(callSite, tokens, budget.maxTokens(), usd, budget.maxUsd()));
        }
        warnIfClose(callSite, budget, tokens, usd);
    }

    /** Prompt + completion tokens this run has already paid for, across every call site. */
    public long tokensSpent() {
        return (long) sum(registry.find("llm.tokens").counters());
    }

    /**
     * Dollars this run has already paid for. Always {@code 0} while {@code llm.pricing} is unset,
     * which is the shipped default — the reason the token ceiling exists at all.
     */
    public double usdSpent() {
        return sum(registry.find("llm.cost.usd").counters());
    }

    private static boolean overTokens(LlmProperties.Budget budget, long tokens) {
        return budget.maxTokens() > 0 && tokens >= budget.maxTokens();
    }

    private static boolean overUsd(LlmProperties.Budget budget, double usd) {
        return budget.maxUsd() > 0 && usd >= budget.maxUsd();
    }

    /**
     * One line while there is still headroom, so the ceiling can be calibrated from real runs
     * instead of from the guess its default necessarily is.
     */
    private static void warnIfClose(String callSite, LlmProperties.Budget budget, long tokens, double usd) {
        boolean closeOnTokens = budget.maxTokens() > 0 && tokens >= budget.maxTokens() * WARN_AT;
        boolean closeOnUsd = budget.maxUsd() > 0 && usd >= budget.maxUsd() * WARN_AT;
        if (closeOnTokens || closeOnUsd) {
            log.warn("budget-ceiling near limit before call={} spent_tokens={}/{} spent_usd={}/{}",
                    callSite, tokens, budget.maxTokens(), String.format(java.util.Locale.ROOT, "%.4f", usd), budget.maxUsd());
        }
    }

    private static double sum(Collection<Counter> counters) {
        return List.copyOf(counters).stream().mapToDouble(Counter::count).sum();
    }
}
