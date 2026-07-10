package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The week in signals: what climbed, what held, and what came to nothing. Rendered only in the
 * Friday edition, which is what gives the Mon/Wed/Fri rhythm a culmination instead of three
 * interchangeable mails.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyRecap(List<RecapEntry> entries) {

    public WeeklyRecap {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }
}
