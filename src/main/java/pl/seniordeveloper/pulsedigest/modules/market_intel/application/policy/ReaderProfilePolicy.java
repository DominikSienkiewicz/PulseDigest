package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Narrow policy governing the living reader model. Owned by the application layer so the pipeline
 * does not depend on infrastructure-bound configuration types.
 *
 * <p>Every field is a guard against profile drift, which is the failure mode this feature has:
 * {@code minVotes} refuses to model a reader from noise, {@code refreshDays} keeps it to one cheap
 * call a week rather than one per run, and {@code hypothesisTtlDays} lets a claim the reader stopped
 * confirming expire instead of steering the digest forever.
 *
 * @param enabled           whether the reader model is distilled and injected at all
 * @param minVotes          clicks required before a profile is distilled for the first time
 * @param refreshDays       how often the profile is re-distilled
 * @param hypothesisTtlDays how long a single hypothesis survives without fresh confirmation
 */
public record ReaderProfilePolicy(boolean enabled, int minVotes, int refreshDays, int hypothesisTtlDays) {
}
