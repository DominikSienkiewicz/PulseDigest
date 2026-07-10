package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Coverage report for the reader's watched technologies, scanned across every headline this run
 * fetched — not just the ones that survived the prompt budget.
 *
 * <p>That distinction is the feature. Silence in a digest otherwise cannot be told apart from an
 * item being trimmed, so the reader can never be sure nothing was missed. An explicit
 * "Spring AI: 0 wzmianek" is confirmed silence.
 */
public record WatchlistScan(List<WatchlistHit> hits) {

    public WatchlistScan {
        hits = hits != null ? List.copyOf(hits) : List.of();
    }

    /**
     * Scans headlines for each keyword, in the configured order, keeping zero-mention keywords.
     * Matching reuses {@link TechDemandAggregator} so "go" does not match "Golang".
     */
    public static WatchlistScan of(List<String> headlines, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new WatchlistScan(List.of());
        }
        Map<String, Integer> counts = TechDemandAggregator.countMentions(headlines, keywords);
        return new WatchlistScan(keywords.stream()
                .map(keyword -> new WatchlistHit(keyword, counts.getOrDefault(keyword.toLowerCase(), 0)))
                .toList());
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }
}
