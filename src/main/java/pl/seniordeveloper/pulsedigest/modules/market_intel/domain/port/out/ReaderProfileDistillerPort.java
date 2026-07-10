package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;

import java.util.Optional;

/**
 * Distils accumulated votes into a handful of dated, evidenced hypotheses about the reader.
 *
 * <p>Returns {@link Optional#empty()} on any failure. A failed distillation must leave the previous
 * profile standing: months of accumulated model are not worth losing to one bad LLM call.
 */
public interface ReaderProfileDistillerPort {

    Optional<ReaderProfile> distil(ProfileEvidence evidence);
}
