package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

/**
 * Port for synthesizing intelligence results into a structured report using AI.
 */
public interface LlmSynthesisPort {
    ReportData synthesize(ResearchResult research);
}
