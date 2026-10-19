package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A conference call for papers that is still open (N09).
 *
 * <p>The adapter that reads conference data surfaces talks that already happened, which the reader
 * can do nothing with. The actionable half is the deadline: a date to submit by, which is the only
 * part of a conference a reader can act on months in advance — and speaking is portfolio work, so
 * missing one is a real cost.
 *
 * @param conference the event
 * @param url        where to submit
 * @param deadline   the last day to submit
 * @param location   city and country, or "online"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallForPapers(String conference, String url, LocalDate deadline, String location) {

    /** Whole days left. Negative once the deadline has passed, which is how a stale entry shows. */
    @JsonIgnore
    public long daysLeft(LocalDate today) {
        return ChronoUnit.DAYS.between(today, deadline);
    }

    /** Closing within a week — the ones worth saying so about. */
    @JsonIgnore
    public boolean isUrgent(LocalDate today) {
        long left = daysLeft(today);
        return left >= 0 && left <= 7;
    }
}
