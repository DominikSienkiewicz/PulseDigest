package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads reader feedback from the {@code feedback} table (written by an external receiver from the
 * email's 👍/👎 links): down-voted URLs to suppress before LLM scoring, and net votes per source to
 * nudge source weights in signal scoring. The headless batch only reads — it never serves the HTTP
 * endpoint that records the clicks.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabaseFeedbackAdapter implements FeedbackPort {

    private static final String DOWNVOTED_SQL = """
            SELECT DISTINCT item_url
            FROM feedback
            WHERE vote = 'DOWN' AND created_at >= ?
            """;

    // 'UP' only, deliberately: 'SAVE' means "later", which is not a reader confirming the item was
    // worth a 🔴 slot. Folding saves in here would re-merge the two signals N05 separated.
    private static final String UPVOTED_SQL = """
            SELECT DISTINCT item_url
            FROM feedback
            WHERE vote = 'UP' AND created_at >= ?
            """;

    // One row per URL even if it was saved out of two editions, keeping the EARLIEST save: how long
    // something has been sitting there is the whole point of the reminder. The NOT EXISTS drops
    // anything the reader later voted on, in any edition — a vote is them coming back to it, which is
    // the only evidence of "dealt with" this schema can offer.
    private static final String SAVED_SQL = """
            SELECT DISTINCT ON (f.item_url) f.item_url, f.source, f.category, f.created_at
            FROM feedback f
            WHERE f.vote = 'SAVE' AND f.created_at >= ?
              AND NOT EXISTS (
                  SELECT 1 FROM feedback v
                  WHERE v.item_url = f.item_url AND v.vote IN ('UP', 'DOWN')
              )
            ORDER BY f.item_url, f.created_at
            """;

    // Every vote value is spelled out and the fallback is 0, not -1 (N05). The shorter
    // `CASE vote WHEN 'UP' THEN 1 ELSE -1 END` quietly turned every value that was not 'UP' into a
    // down-vote, so the day a third action shipped a bookmark would have become a penalty.
    private static final String NET_VOTES_SQL = """
            SELECT source, SUM(CASE vote WHEN 'UP' THEN 1 WHEN 'DOWN' THEN -1 ELSE 0 END) AS net_votes
            FROM feedback
            WHERE created_at >= ? AND source IS NOT NULL
            GROUP BY source
            """;

    private static final String NET_VOTES_BY_CATEGORY_SQL = """
            SELECT lower(category) AS category,
                   SUM(CASE vote WHEN 'UP' THEN 1 WHEN 'DOWN' THEN -1 ELSE 0 END) AS net_votes
            FROM feedback
            WHERE created_at >= ? AND category IS NOT NULL AND category <> ''
            GROUP BY lower(category)
            """;

    private final JdbcClient jdbcClient;

    @Override
    public Map<String, Integer> netVotesByCategory(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        Map<String, Integer> netVotes = jdbcClient.sql(NET_VOTES_BY_CATEGORY_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> Map.entry(rs.getString("category"), rs.getInt("net_votes")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        log.info("Feedback: net votes across {} categorized topic(s) over the last {}d",
                netVotes.size(), lookbackDays);
        return netVotes;
    }

    @Override
    public Set<String> downvotedUrls(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<String> urls = jdbcClient.sql(DOWNVOTED_SQL)
                .param(cutoff)
                .query(String.class)
                .list();
        if (!urls.isEmpty()) {
            log.info("Feedback: {} down-voted URL(s) suppressed (last {}d)", urls.size(), lookbackDays);
        }
        return Set.copyOf(urls);
    }

    @Override
    public Set<String> upvotedUrls(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<String> urls = jdbcClient.sql(UPVOTED_SQL)
                .param(cutoff)
                .query(String.class)
                .list();
        log.debug("Feedback: {} up-voted URL(s) in the last {}d", urls.size(), lookbackDays);
        return Set.copyOf(urls);
    }

    @Override
    public List<SavedItem> savedItems(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<SavedItem> saved = jdbcClient.sql(SAVED_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> new SavedItem(
                        null,
                        rs.getString("item_url"),
                        rs.getString("source"),
                        rs.getString("category"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list()
                .stream()
                .sorted(Comparator.comparing(SavedItem::savedAt))
                .toList();
        if (!saved.isEmpty()) {
            log.info("Feedback: {} saved item(s) still unanswered (last {}d)", saved.size(), lookbackDays);
        }
        return saved;
    }

    @Override
    public Map<String, Integer> netVotesBySource(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        Map<String, Integer> netVotes = jdbcClient.sql(NET_VOTES_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> Map.entry(rs.getString("source"), rs.getInt("net_votes")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!netVotes.isEmpty()) {
            log.info("Feedback: net votes by source (last {}d): {}", lookbackDays, netVotes);
        }
        return netVotes;
    }
}
