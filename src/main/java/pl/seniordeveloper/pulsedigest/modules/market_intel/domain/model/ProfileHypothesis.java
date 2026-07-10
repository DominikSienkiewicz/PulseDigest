package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One claim the digest believes about its reader, and the evidence it was distilled from.
 *
 * <p>{@code evidence} is not decoration. A profile that cannot show its working is a horoscope: the
 * reader has no way to tell a real pattern from a model's confabulation, and neither has anyone
 * debugging why the digest drifted. {@code observedAt} is what lets a stale claim expire rather than
 * steer the digest forever.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileHypothesis(
        String statement,
        String evidence,
        @JsonProperty("observed_at") LocalDate observedAt
) {

    /** Whether this claim has outlived the window in which the reader last confirmed it. */
    public boolean isExpiredOn(LocalDate today, int ttlDays) {
        return observedAt == null || observedAt.plusDays(ttlDays).isBefore(today);
    }
}
