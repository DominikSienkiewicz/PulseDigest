package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * One ranked technology in the job-market demand signal: how many hiring posts mentioned it.
 *
 * @param name     technology label as configured in the counting vocabulary
 * @param mentions number of hiring posts (HN "Who is hiring?" comments) that mentioned it
 */
public record TechDemandEntry(String name, int mentions) {
}
