package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * What the digest has learned about its reader: a small set of dated, evidenced hypotheses distilled
 * from accumulated 👍/👎 votes.
 *
 * <p>The persona in {@code interest-profile.persona} is a frozen string — after a year the digest
 * knows exactly as much about the reader as on day one. This is the part that compounds. It is also
 * the part that can drift, so every hypothesis carries an observation date and expires, and the whole
 * profile is versioned append-only rather than overwritten.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReaderProfile(
        @JsonProperty("distilled_at") Instant distilledAt,
        @JsonProperty("vote_count") int voteCount,
        List<ProfileHypothesis> hypotheses
) {

    public ReaderProfile {
        hypotheses = hypotheses != null ? List.copyOf(hypotheses) : List.of();
    }

    /** This profile with expired hypotheses pruned — what the prompt and the footer actually see. */
    public ReaderProfile activeOn(LocalDate today, int hypothesisTtlDays) {
        return new ReaderProfile(distilledAt, voteCount, hypotheses.stream()
                .filter(h -> !h.isExpiredOn(today, hypothesisTtlDays))
                .toList());
    }

    /** Whether the profile is old enough to be worth re-distilling. */
    public boolean isStaleOn(LocalDate today, int refreshDays) {
        if (distilledAt == null) {
            return true;
        }
        LocalDate nextRefresh = distilledAt.atZone(ZoneOffset.UTC).toLocalDate().plusDays(refreshDays);
        return !today.isBefore(nextRefresh);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return hypotheses.isEmpty();
    }
}
