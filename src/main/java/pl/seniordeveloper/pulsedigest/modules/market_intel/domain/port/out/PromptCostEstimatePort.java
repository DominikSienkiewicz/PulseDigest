package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptCostEstimate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

/**
 * Estimates what synthesising a given input would cost, without synthesising it.
 *
 * <p>Exists so the application layer can tell an operator what a prompt replay will spend before it
 * spends it. The estimate is built from the real prompt the real builder would send, not from a
 * guess about the input's size — a figure that is only roughly right is still worth printing, a
 * figure that is unrelated to the actual request is not.
 */
public interface PromptCostEstimatePort {

    PromptCostEstimate estimate(ResearchResult research);
}
