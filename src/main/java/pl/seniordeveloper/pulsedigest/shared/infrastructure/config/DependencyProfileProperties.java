package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * The reader's own stack: where to find it, and what to assume when it cannot be found.
 *
 * <p>{@code projectPaths} is empty by default and must stay that way in this repository. Those paths
 * point at the reader's other checkouts, which exist on their machine and not in CI, so a non-empty
 * default would make the scheduled digest depend on directories it will never see. With no readable
 * path the run falls back to {@code coordinates} and says which list it used (N07).
 *
 * @param enabled      whether the release radar and stack matching run at all
 * @param projectPaths directories or build files to scan, e.g. {@code ~/Dev/my-api}
 * @param coordinates  declared fallback, as {@code group:artifact:version} (version optional)
 */
@ConfigurationProperties(prefix = "report.dependency-profile")
@Validated
public record DependencyProfileProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue List<String> projectPaths,
        @DefaultValue List<String> coordinates
) {

    public DependencyProfileProperties {
        projectPaths = projectPaths != null ? List.copyOf(projectPaths) : List.of();
        coordinates = coordinates != null ? List.copyOf(coordinates) : List.of();
    }
}
