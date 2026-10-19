package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;

import java.util.List;

/**
 * Finds which of the reader's own dependencies have a newer release than the version they pin.
 *
 * <p>Deliberately not the {@code github-releases} source, which watches a hand-listed roster of
 * repositories: a Maven coordinate is not a GitHub repository, and mapping between them would need a
 * hand-maintained alias table that drifts on every new dependency. The registry that knows which
 * version of an artifact is current is the registry the artifact is published to.
 */
public interface ReleaseRadarPort {

    /** Outdated dependencies, newest-gap first. Never throws; an unreachable registry yields none. */
    List<DependencyRelease> outdated(DependencyProfile profile);
}
