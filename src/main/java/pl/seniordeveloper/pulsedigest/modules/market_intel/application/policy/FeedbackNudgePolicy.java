package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Narrow policy controlling the C6 feedback-driven source-weight nudge. Owned by the application
 * layer so {@code GenerateMarketReportProcessor} does not depend on infrastructure-bound
 * configuration types.
 *
 * <p>Wired in {@code market_intel/infrastructure/config} from the bound {@code FeedbackProperties}
 * (the same {@code enabled} flag and {@code lookbackDays} window that govern down-vote suppression).
 *
 * @param enabled      whether accumulated feedback nudges per-source weights this run
 * @param lookbackDays how far back to aggregate net votes per source
 */
public record FeedbackNudgePolicy(boolean enabled, int lookbackDays) {
}
