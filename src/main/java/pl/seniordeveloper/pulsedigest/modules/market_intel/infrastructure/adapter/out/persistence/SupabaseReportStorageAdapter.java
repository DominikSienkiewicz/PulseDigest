package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Stores an edition in Supabase (Postgres) as one row per job id, with the whole
 * {@link PersistedReport} in a single JSONB column (see
 * <a href="../../../../../../../../../../docs/adr/0003-jsonb-payload-with-domain-records.md">ADR-0003</a>).
 *
 * <p>Write-only. The {@code getLatest()} read disappeared on 2026-10-18 along with the query
 * services nothing called; reading the archive belongs to {@code ReportHistoryPort}, which reaches
 * inside the payload rather than fetching whole editions. There was never an in-memory cache to
 * justify either — this is a one-shot batch: start, run, exit.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabaseReportStorageAdapter implements ReportStoragePort {

    private static final String UPSERT_SQL = """
            INSERT INTO reports (job_id, generated_at, payload)
            VALUES (?, ?, ?::jsonb)
            ON CONFLICT (job_id) DO UPDATE SET
                generated_at = EXCLUDED.generated_at,
                payload      = EXCLUDED.payload
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public void save(PersistedReport report) {
        try {
            String payload = objectMapper.writeValueAsString(report);
            int rows = jdbcClient.sql(UPSERT_SQL)
                    .params(
                            report.jobId(),
                            OffsetDateTime.ofInstant(report.generatedAt(), ZoneOffset.UTC),
                            payload
                    )
                    .update();
            log.info("Report persisted to Supabase | jobId={} | rows={}", report.jobId(), rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize report payload", e);
        }
    }
}
