package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Narrow policy controlling how far back the digest remembers its own editions. Owned by the
 * application layer so the pipeline does not depend on infrastructure-bound configuration types.
 *
 * <p>Wired in {@code market_intel/infrastructure/config} from the bound
 * {@code ReportHistoryProperties}.
 *
 * @param enabled      whether report history is read this run
 * @param lookbackDays how far back past editions are read
 */
public record ReportHistoryPolicy(boolean enabled, int lookbackDays) {
}
