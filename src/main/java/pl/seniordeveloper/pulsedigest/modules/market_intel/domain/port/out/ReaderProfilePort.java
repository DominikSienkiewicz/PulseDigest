package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;

import java.util.Optional;

/**
 * Append-only store of distilled reader profiles. Versions are never overwritten: a profile that
 * drifted must remain inspectable next to the one that replaced it.
 */
public interface ReaderProfilePort {

    /** The most recently distilled profile, if the reader has ever voted enough to earn one. */
    Optional<ReaderProfile> latest();

    /** Appends a new version. */
    void save(ReaderProfile profile);
}
