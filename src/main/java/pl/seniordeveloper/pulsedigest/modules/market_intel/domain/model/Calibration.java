package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How often the reader agreed with the 🔴 badge (N12).
 *
 * <p>The feedback loop was an input the reader could not see working. This turns it into something
 * visible: of the stories past editions called CRITICAL, what share did he actually up-vote. It is
 * also the badge's only self-assessment that comes from outside the system — {@code RadarAccuracy}
 * grades the radar's predictions against the scorer's own later verdict, which is the digest marking
 * its own homework.
 *
 * @param criticalItems stories ranked CRITICAL in the window
 * @param upvoted       how many of those the reader up-voted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Calibration(
        @JsonProperty("critical_items") int criticalItems,
        @JsonProperty("upvoted") int upvoted
) {

    /**
     * Whether there is enough to report. One 🔴 in a month makes a "100%" hit rate that says nothing,
     * and a line that overclaims from two data points is worse than no line.
     */
    @JsonIgnore
    public boolean isReportable() {
        return criticalItems >= 3;
    }

    @JsonIgnore
    public int percent() {
        return criticalItems == 0 ? 0 : (int) Math.round(100.0 * upvoted / criticalItems);
    }
}
