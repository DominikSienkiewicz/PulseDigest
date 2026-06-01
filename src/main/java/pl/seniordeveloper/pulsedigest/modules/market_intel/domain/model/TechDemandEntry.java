package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * One technology in the job-market demand ranking.
 *
 * @param name     technology label as configured in the counting vocabulary
 * @param mentions number of hiring posts (HN "Who is hiring?" comments) mentioning it this month
 * @param share    fraction of hiring posts mentioning it this month (0.0–1.0)
 * @param deltaPp  change in {@code share} versus the previous month's thread, in percentage points
 *                 (e.g. {@code +3.2} = up 3.2pp); {@code null} when no previous thread was available
 */
public record TechDemandEntry(String name, int mentions, double share, Double deltaPp) {
}
