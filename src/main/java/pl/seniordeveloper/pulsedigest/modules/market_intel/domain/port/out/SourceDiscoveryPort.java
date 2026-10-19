package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceProposal;

import java.util.List;

/** Asks the model which sources the published record argues for (N28). */
public interface SourceDiscoveryPort {

    /**
     * @param bestItems the highest-ranked items already published, which are the evidence
     * @return proposals, or empty when the call fails — never a partial guess
     */
    List<SourceProposal> propose(List<DigestItem> bestItems);
}
