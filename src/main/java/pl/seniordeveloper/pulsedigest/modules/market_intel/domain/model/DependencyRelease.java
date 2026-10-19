package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A dependency the reader pins behind its latest release (N07).
 *
 * <p>This is the one thing a general news digest structurally cannot produce: not "Spring Boot 4.1.2
 * is out", which every feed carries, but "Spring Boot 4.1.2 is out and you pin 4.1.0".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DependencyRelease(DependencyCoordinate coordinate, String latestVersion) {

    /**
     * {@code group:artifact pinned → latest}, as the radar line reads it.
     *
     * <p>{@code @JsonIgnore} because this is derived: Jackson would otherwise write it as a
     * {@code summary} field that no constructor can read back, which is what breaks the stored
     * payload's round trip — the same trap {@code ResearchResult} fell into.
     */
    @JsonIgnore
    public String summary() {
        return coordinate.key() + " " + coordinate.pinnedVersion() + " → " + latestVersion;
    }
}
