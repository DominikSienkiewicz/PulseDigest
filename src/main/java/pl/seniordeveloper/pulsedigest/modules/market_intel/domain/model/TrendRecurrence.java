package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * How long a story has been building: how many consecutive editions (including the current one)
 * carried it, and when the reader first saw it. {@code firstSeenAt} is null for a story appearing
 * for the first time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrendRecurrence(int editionStreak, LocalDate firstSeenAt) {

    /** A story is "building" once it has survived into at least a second consecutive edition. */
    @JsonIgnore
    public boolean isBuilding() {
        return editionStreak >= 2;
    }
}
