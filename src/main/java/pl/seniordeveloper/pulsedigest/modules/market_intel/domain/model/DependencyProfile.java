package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reader's dependencies, as this run was able to determine them.
 *
 * <p>{@code fromBuildFiles} is the honesty flag the feature cannot work without. Reading build files
 * outside the repository makes a run environment-dependent: those projects exist on the author's
 * machine and not in CI, so the scheduled digest necessarily falls back to the coordinates declared
 * in configuration. A digest that silently reported a narrower stack would be worse than one that
 * says which list it used (N07).
 *
 * @param coordinates    every usable coordinate found, de-duplicated by {@code group:artifact}
 * @param fromBuildFiles whether any build file was actually read
 * @param scannedPaths   the paths that were read, for the line that says so
 */
public record DependencyProfile(List<DependencyCoordinate> coordinates, boolean fromBuildFiles,
                                List<String> scannedPaths) {

    public DependencyProfile {
        coordinates = coordinates != null ? List.copyOf(coordinates) : List.of();
        scannedPaths = scannedPaths != null ? List.copyOf(scannedPaths) : List.of();
    }

    public static DependencyProfile empty() {
        return new DependencyProfile(List.of(), false, List.of());
    }

    public boolean isEmpty() {
        return coordinates.isEmpty();
    }

    /** Every {@code group:artifact} in the profile — what an advisory's package name is matched against. */
    public Set<String> keys() {
        return coordinates.stream().map(DependencyCoordinate::key).collect(Collectors.toSet());
    }

    /** The coordinate behind a {@code group:artifact}, for naming the pin in a radar line. */
    public Map<String, DependencyCoordinate> byKey() {
        return coordinates.stream().collect(Collectors.toMap(
                DependencyCoordinate::key, Function.identity(), (first, second) -> first));
    }
}
