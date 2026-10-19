package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import java.util.Optional;

/**
 * Stores and reloads the input a past edition was synthesised from.
 *
 * <p>Every edition's output is archived; its input never was, which made "would the new prompt have
 * done better last Thursday?" unanswerable in principle rather than merely untried (N24). With the
 * input stored, each past edition becomes a regression corpus.
 *
 * <p>What is stored is the {@link ResearchResult} the prompt was actually built from — not the raw
 * HTTP bodies of nineteen adapters. That is the deliberate granularity: the raw bodies would require
 * intercepting every adapter and would capture bytes the pipeline never looked at, while the
 * {@code ResearchResult} is precisely the boundary the prompt sees. It already round-trips through
 * Jackson, which the committed edition fixture proves on every build.
 */
public interface EditionInputPort {

    /** Stores the synthesis input for an edition that has already been persisted. Never throws. */
    void save(String jobId, ResearchResult research);

    /** The stored input for an edition, or empty when that edition has none. */
    Optional<ResearchResult> load(String jobId);
}
