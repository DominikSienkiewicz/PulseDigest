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
import java.util.Optional;

/**
 * Persystencja raportów w Supabase (Postgres) jako pojedynczy wiersz per jobId
 * z całym {@link PersistedReport} jako kolumna JSONB.
 *
 * <p>Bez cache w pamięci — aplikacja jest one-shot batch (start → cykl → exit),
 * więc TTL cache nie ma czasu być reused. Każde {@code getLatest()} = jeden query.
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

    private static final String LATEST_SQL = """
            SELECT payload FROM reports
            ORDER BY generated_at DESC
            LIMIT 1
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

    @Override
    public Optional<PersistedReport> getLatest() {
        return jdbcClient.sql(LATEST_SQL)
                .query((rs, rowNum) -> deserialize(rs.getString("payload")))
                .optional();
    }

    private PersistedReport deserialize(String json) {
        try {
            return objectMapper.readValue(json, PersistedReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupted report payload in DB", e);
        }
    }
}
