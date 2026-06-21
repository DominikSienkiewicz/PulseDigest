package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private static final String NET_VOTES_SQL = """
            SELECT source, SUM(CASE vote WHEN 'UP' THEN 1 ELSE -1 END) AS net_votes
            FROM feedback
            WHERE created_at >= ? AND source IS NOT NULL
            GROUP BY source
            """;

    private final JdbcClient jdbcClient;

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
