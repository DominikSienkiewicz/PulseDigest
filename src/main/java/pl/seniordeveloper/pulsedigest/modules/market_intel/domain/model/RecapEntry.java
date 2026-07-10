package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * One line of the weekly recap: a story, where it started the week, and where it ended up.
 */
public record RecapEntry(
        String title,
        String url,
        RecapChange change,
        SignalRank previousRank,
        SignalRank currentRank
) {
}
