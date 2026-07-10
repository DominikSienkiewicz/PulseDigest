package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * How many of this run's fetched headlines mentioned a watched technology. Zero is a result, not an
 * absence — it is the difference between "nothing happened" and "it never reached the digest".
 */
public record WatchlistHit(String keyword, int mentions) {
}
