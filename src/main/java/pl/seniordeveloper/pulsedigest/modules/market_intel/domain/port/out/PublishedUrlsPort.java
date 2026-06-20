package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import java.util.Set;

/**
 * Output port exposing item URLs already published in recent digest editions, so the pipeline can
 * suppress cross-edition duplicates when widened lookback windows overlap consecutive runs.
 */
public interface PublishedUrlsPort {

    /**
     * Canonical URLs that appeared in any edition persisted within the last {@code lookbackDays} days.
     *
     * @param lookbackDays how far back across prior editions to collect URLs
     * @return canonical URLs already published (empty when there is no history)
     */
    Set<String> recentlyPublishedUrls(int lookbackDays);
}
