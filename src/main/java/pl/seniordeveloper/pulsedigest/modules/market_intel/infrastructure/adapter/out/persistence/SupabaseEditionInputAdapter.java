package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;

import java.util.Optional;

/**
 * Persists each edition's synthesis input as a single JSONB document in {@code edition_inputs}.
 *
 * <p>A failed capture is logged and swallowed: the edition itself is already stored and delivered by
 * the time this runs, and losing the ability to replay one past run is not worth failing a digest
 * that otherwise succeeded.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabaseEditionInputAdapter implements EditionInputPort {

    private static final String UPSERT_SQL = """
            INSERT INTO edition_inputs (job_id, payload)
            VALUES (?, ?::jsonb)
            ON CONFLICT (job_id) DO UPDATE SET
                payload     = EXCLUDED.payload,
                captured_at = now()
            """;

    private static final String LOAD_SQL = "SELECT payload FROM edition_inputs WHERE job_id = ?";

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String jobId, ResearchResult research) {
        if (jobId == null || research == null) {
            return;
        }
        try {
            int rows = jdbcClient.sql(UPSERT_SQL)
                    .params(jobId, objectMapper.writeValueAsString(research))
                    .update();
            log.info("Edition input captured | jobId={} | rows={}", jobId, rows);
        } catch (Exception e) {
            log.warn("Could not capture the edition input for jobId={}: {}", jobId, e.getMessage());
        }
    }

    @Override
    public Optional<ResearchResult> load(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql(LOAD_SQL)
                .param(jobId)
                .query((rs, rowNum) -> deserialize(rs.getString("payload")))
                .optional();
    }

    private ResearchResult deserialize(String json) {
        try {
            return objectMapper.readValue(json, ResearchResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupted edition input payload in DB", e);
        }
    }
}
