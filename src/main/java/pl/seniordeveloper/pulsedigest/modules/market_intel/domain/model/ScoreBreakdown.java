package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Why this item scored what it scored — every component the scorer already computed and then threw
 * away.
 *
 * <p>Scoring was a black box: the reader could not see why an item surfaced, nor what his own 👍/👎
 * had done to it. A vote whose effect is invisible is a vote nobody casts twice, which is how a
 * learning loop starves.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreBreakdown(
        @JsonProperty("source_key") String sourceKey,
        @JsonProperty("base_weight") double baseWeight,
        @JsonProperty("effective_weight") double effectiveWeight,
        @JsonProperty("net_source_votes") int netSourceVotes,
        @JsonProperty("engagement_bonus") int engagementBonus,
        @JsonProperty("category_multiplier") double categoryMultiplier,
        @JsonProperty("net_category_votes") int netCategoryVotes,
        @JsonProperty("cross_source_bonus") int crossSourceBonus
) {

    /** The credibility component, on the same 0–100 scale as the final signal score. */
    @JsonIgnore
    public int baseScore() {
        return (int) Math.round(effectiveWeight * 100);
    }

    /** Whether the reader's votes moved this source's credibility weight. */
    @JsonIgnore
    public boolean sourceWeightWasNudged() {
        return netSourceVotes != 0;
    }

    /** Whether the reader expressed a preference on this item's category. */
    @JsonIgnore
    public boolean categoryWasNudged() {
        return netCategoryVotes != 0;
    }

    /** Whether three or more independent source domains carried this story. */
    @JsonIgnore
    public boolean wasCrossSourceConfirmed() {
        return crossSourceBonus > 0;
    }

    /** Whether anything the reader clicked contributed to this score. */
    @JsonIgnore
    public boolean readerInfluenced() {
        return sourceWeightWasNudged() || categoryWasNudged();
    }
}
