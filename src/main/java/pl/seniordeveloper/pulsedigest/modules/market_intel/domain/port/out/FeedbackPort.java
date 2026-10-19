package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;

import java.util.List;
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
     * Canonical URLs the reader up-voted within the last {@code lookbackDays}.
     *
     * <p>Not a mirror of {@link #downvotedUrls} in purpose: down-votes are acted on (suppression),
     * up-votes are <em>reported</em>. They answer the question the badge cannot answer about itself
     * — of the stories this digest called 🔴, how many did the reader agree were worth it (N12).
     */
    Set<String> upvotedUrls(int lookbackDays);

    /**
     * Items the reader kept for later and has not voted on since, within the last
     * {@code lookbackDays}, earliest save first.
     *
     * <p>"Has not voted on" is as close to "unread" as this data can honestly get: the batch serves
     * no HTTP, nothing observes the reader opening a link, and a save carries no title. What a
     * later 👍 or 👎 on the same URL does prove is that the reader came back to it and formed an
     * opinion, so those URLs drop off the shelf. Everything else stays until it ages out of the
     * window.
     *
     * @param lookbackDays how far back saves are honoured
     * @return saved items without titles, earliest first (empty when nothing is saved)
     */
    List<SavedItem> savedItems(int lookbackDays);

    /**
     * Net (UP − DOWN) vote count per source within the last {@code lookbackDays}, used to nudge the
     * per-source credibility weight in signal scoring (C6). Sources with no feedback are absent from
     * the map (treated as zero net votes).
     *
     * @param lookbackDays how far back to aggregate votes
     * @return source label → net vote count (empty when there is no feedback)
     */
    Map<String, Integer> netVotesBySource(int lookbackDays);

    /**
     * Net (UP − DOWN) vote count per item category (lower-cased) within the last {@code lookbackDays}.
     * A down-vote on a dull paper should punish the topic, not the whole of arXiv.
     *
     * <p>Rows written before the receiver started sending {@code category} carry a null value and are
     * excluded, so this degrades to an empty map rather than to a wrong one.
     *
     * @param lookbackDays how far back to aggregate votes
     * @return lower-cased category → net vote count (empty when there is no categorized feedback)
     */
    Map<String, Integer> netVotesByCategory(int lookbackDays);
}
