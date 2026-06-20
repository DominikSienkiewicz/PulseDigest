package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Reads canonical item URLs from recent persisted editions (the existing {@code reports} JSONB
 * payload, path {@code payload->'report'->'items'}) so the prompt builder can drop cross-edition
 * duplicates before LLM scoring. No new table — reuses the report history already stored per run.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabasePublishedUrlsAdapter implements PublishedUrlsPort {

    private static final String RECENT_URLS_SQL = """
            SELECT DISTINCT item->>'url' AS url
            FROM reports, jsonb_array_elements(payload->'report'->'items') AS item
            WHERE generated_at >= ? AND item->>'url' IS NOT NULL
            """;

    private final JdbcClient jdbcClient;

    @Override
    public Set<String> recentlyPublishedUrls(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<String> urls = jdbcClient.sql(RECENT_URLS_SQL)
                .param(cutoff)
                .query(String.class)
                .list();
        log.info("Cross-edition dedup: {} URLs published in editions over the last {}d", urls.size(), lookbackDays);
        return Set.copyOf(urls);
    }
}
