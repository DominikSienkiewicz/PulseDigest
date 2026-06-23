package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.Map;

/**
 * One month's technology-mention snapshot: the thread's month label, the per-token mention counts,
 * and the total number of hiring posts scanned. Bundles the three values that always travel together
 * through {@link TechDemandPulse#assemble}.
 *
 * @param label  the thread's month label (e.g. {@code "June 2026"}); may be {@code null}
 * @param counts technology token → number of posts mentioning it (never {@code null}; use an empty map)
 * @param total  number of hiring posts scanned for this month
 */
public record MonthMentions(String label, Map<String, Integer> counts, int total) {
}
