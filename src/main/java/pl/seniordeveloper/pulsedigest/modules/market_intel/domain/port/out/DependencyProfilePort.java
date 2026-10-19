package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;

/**
 * Determines which dependencies the reader actually ships.
 *
 * <p>Reads the build files of the reader's own projects where they exist and degrades to the
 * coordinates declared in configuration where they do not — which is the normal case in CI. Never
 * throws and never executes a build: the parse is textual, coordinates only.
 */
public interface DependencyProfilePort {

    DependencyProfile load();
}
