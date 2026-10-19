package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Locale;

/**
 * One Maven coordinate the reader actually depends on, with the version they pin.
 *
 * <p>Shared by the two features that need to know the reader's stack rather than their topics: the
 * release radar (N07) asks Maven Central whether a newer version exists, and the advisory matcher
 * (N06) compares these against the packages an advisory names. One model because one list — two
 * lists of the same dependencies in configuration would drift, which is the defect F8 reported about
 * numbers kept in two places.
 *
 * @param groupId       Maven group, e.g. {@code org.springframework.boot}
 * @param artifactId    Maven artifact, e.g. {@code spring-boot-starter}
 * @param pinnedVersion the version the reader's build declares, or blank when it is managed elsewhere
 * @param origin        where this came from — a build file path, or {@code "configuration"}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DependencyCoordinate(String groupId, String artifactId, String pinnedVersion, String origin) {

    public DependencyCoordinate {
        groupId = groupId != null ? groupId.strip() : "";
        artifactId = artifactId != null ? artifactId.strip() : "";
        pinnedVersion = pinnedVersion != null ? pinnedVersion.strip() : "";
        origin = origin != null ? origin.strip() : "";
    }

    /** {@code group:artifact} — how both GitHub advisories and Maven Central name an artifact. */
    @JsonIgnore
    public String key() {
        return groupId + ":" + artifactId;
    }

    /** Whether this is a usable coordinate at all. */
    @JsonIgnore
    public boolean isComplete() {
        return !groupId.isBlank() && !artifactId.isBlank();
    }

    /**
     * Whether the pinned version is a pre-release.
     *
     * <p>Maven Central only knows releases, so comparing a `-SNAPSHOT`, `-RC1` or `-M2` pin against
     * its "latest" reports a *lower* number as an upgrade — this project pins Spring Boot
     * {@code 4.1.0-SNAPSHOT} while Central's latest is 3.5.x. A pre-release pin is a deliberate
     * choice to be ahead of releases, so the radar skips it instead of nagging about going backwards.
     */
    @JsonIgnore
    public boolean isPreRelease() {
        String v = pinnedVersion.toLowerCase(Locale.ROOT);
        return v.contains("snapshot") || v.contains("-rc") || v.contains("-m")
                || v.contains("alpha") || v.contains("beta") || v.contains("-ea");
    }
}
