package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * The quarterly retrospective's window (N20).
 *
 * @param lookbackDays how far back editions are read — a quarter, not the 28 days a normal run uses
 * @param maturityDays how long a 🔴 call must have had to prove itself before it is graded at all
 */
public record RetrospectivePolicy(int lookbackDays, int maturityDays) {
}
