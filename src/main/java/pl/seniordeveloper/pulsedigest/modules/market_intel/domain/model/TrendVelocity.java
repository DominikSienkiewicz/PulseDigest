package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How fast a story is gathering independent confirmation, measured against its own state a few
 * editions ago: how many source domains it gained, and how much its deterministic signal score rose.
 *
 * <p>{@code criticalCandidate} is the prediction. It is persisted with the signal so a later edition
 * can check whether the prediction came true — a radar that never scores itself is a horoscope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrendVelocity(
        @JsonProperty("domain_growth") int domainGrowth,
        @JsonProperty("score_growth") int scoreGrowth,
        @JsonProperty("critical_candidate") boolean criticalCandidate
) {
}
