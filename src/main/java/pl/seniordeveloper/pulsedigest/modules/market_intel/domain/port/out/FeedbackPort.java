package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import java.util.Map;
import java.util.Set;

/**
 * Output port for reader feedback collected out-of-band. The headless batch never serves HTTP; an
 * external receiver writes feedback rows (from the email's 👍/👎 links) that this port reads on the
 * next run to act on the reader's "less like this" signal.
 */
public interface FeedbackPort {

    /**
     * Canonical URLs the reader down-voted ("less like this") within the last {@code lookbackDays}.
     *
     * @param lookbackDays how far back to honor down-votes
     * @return canonical URLs to suppress (empty when there is no feedback)
     */
    Set<String> downvotedUrls(int lookbackDays);

    /**
     * Net (UP − DOWN) vote count per source within the last {@code lookbackDays}, used to nudge the
     * per-source credibility weight in signal scoring (C6). Sources with no feedback are absent from
     * the map (treated as zero net votes).
     *
     * @param lookbackDays how far back to aggregate votes
     * @return source label → net vote count (empty when there is no feedback)
     */
    Map<String, Integer> netVotesBySource(int lookbackDays);
}
