package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One line of the weekly recap: a story, where it started the week, and where it ended up.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecapEntry(
        String title,
        String url,
        RecapChange change,
        SignalRank previousRank,
        SignalRank currentRank
) {
}
