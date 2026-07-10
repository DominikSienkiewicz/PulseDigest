package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Reads the scored signals of past editions straight out of the {@code reports} JSONB payload.
 * No schema change was needed — every edition has always been stored in full; nothing ever read it.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class SupabaseReportHistoryAdapter implements ReportHistoryPort {

    private static final String RECENT_EDITIONS_SQL = """
            SELECT generated_at, payload->'report'->'signals' AS signals
            FROM reports
            WHERE generated_at >= ? AND payload->'report'->'signals' IS NOT NULL
            ORDER BY generated_at DESC
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PastEdition> recentEditions(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<PastEdition> editions = jdbcClient.sql(RECENT_EDITIONS_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> toEdition(
                        rs.getObject("generated_at", OffsetDateTime.class), rs.getString("signals")))
                .list()
                .stream()
                .filter(edition -> !edition.signals().isEmpty())
                .toList();
        log.debug("Report history: {} past edition(s) within {} days", editions.size(), lookbackDays);
        return editions;
    }

    // A single unparseable legacy payload must not take the run down: skip that edition and move on.
    private PastEdition toEdition(OffsetDateTime generatedAt, String signalsJson) {
        List<Signal> signals = List.of();
        try {
            signals = objectMapper.<List<Signal>>readValue(signalsJson, new TypeReference<List<Signal>>() { })
                    .stream()
                    .filter(signal -> !signal.item().correlationKey().isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("Skipping unreadable edition from {}: {}", generatedAt, e.getMessage());
        }
        return new PastEdition(generatedAt.toInstant(), signals);
    }
}
