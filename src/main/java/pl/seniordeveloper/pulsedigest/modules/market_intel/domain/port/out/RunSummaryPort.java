package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

/**
 * Publishes a human-readable summary of what the run did, where the run itself is visible.
 *
 * <p>On a green run nobody opens the log, so everything the pipeline knows about itself — what each
 * source returned, how many items survived, what the LLM cost — was written to a place no one reads
 * (N35). This port puts the same facts on the run's own page.
 *
 * <p>Best-effort by contract: a digest that was produced and delivered must not fail because its
 * summary could not be written.
 */
public interface RunSummaryPort {

    /** Writes what the run gathered and published. Never throws. */
    void writeIntake(ResearchResult research, int publishedItems, int criticalItems);

    /** Appends the run's terminal outcome. Never throws. */
    void writeOutcome(String status, String detail);
}
