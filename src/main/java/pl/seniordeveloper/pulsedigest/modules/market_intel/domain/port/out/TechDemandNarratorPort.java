package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;

/**
 * Output port for generating the one-sentence interpretation of the tech-demand pulse.
 * Implementations degrade gracefully (return blank) so a failed LLM call never breaks the digest.
 */
public interface TechDemandNarratorPort {

    /** Returns a short Polish interpretation of the ranking, or blank on failure / nothing to say. */
    String narrate(TechDemandSignal signal);
}
