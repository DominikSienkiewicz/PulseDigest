package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import java.util.List;
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

    /**
     * Titles of stories already published, newest edition first. URL dedup cannot see that InfoQ on
     * Monday and Hacker News on Wednesday covered the same story; feeding the titles to the model
     * lets it recognize the repetition semantically.
     *
     * @param lookbackDays how far back across prior editions to collect titles
     * @param maxTitles    hard cap, so the prompt block cannot grow with the archive
     */
    List<String> recentlyPublishedTitles(int lookbackDays, int maxTitles);
}
