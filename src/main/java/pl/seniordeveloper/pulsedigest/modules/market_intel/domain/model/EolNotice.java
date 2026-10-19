package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A piece of the reader's stack that is about to stop being supported, or already has (N08).
 *
 * <p>End-of-life dates are the highest-consequence, lowest-urgency information in a developer's
 * life, which is exactly what a twice-weekly digest is for and exactly what news feeds never carry:
 * nothing *happened*, a date simply arrived.
 *
 * @param name        what reaches end of life, e.g. {@code Eclipse Temurin 26}
 * @param endOfLife   the date support ends
 * @param validAsOf   when the table this came from was last checked — a hand-kept table that cannot
 *                    show its own age is a table nobody can trust (the lesson N01 records)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EolNotice(String name, LocalDate endOfLife, LocalDate validAsOf) {

    /** Already unsupported as of {@code today}. */
    @JsonIgnore
    public boolean isPast(LocalDate today) {
        return endOfLife.isBefore(today);
    }

    /** Whole months from {@code today} until support ends; negative once it has. */
    @JsonIgnore
    public long monthsUntil(LocalDate today) {
        return ChronoUnit.MONTHS.between(today, endOfLife);
    }
}
