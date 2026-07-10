package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.MonthMentions;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandHistoryPort;

import java.util.Map;
import java.util.Optional;

/**
 * Stores one mention snapshot per (month, vocabulary) in {@code tech_demand_history}.
 * A failed read degrades to {@code Optional.empty()} — the caller then re-scrapes as it always did.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class SupabaseTechDemandHistoryAdapter implements TechDemandHistoryPort {

    private static final String FIND_SQL = """
            SELECT month_label, total_postings, counts
            FROM tech_demand_history
            WHERE month_label = ? AND vocabulary_version = ?
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO tech_demand_history (month_label, vocabulary_version, total_postings, counts)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (month_label, vocabulary_version) DO UPDATE SET
                total_postings = EXCLUDED.total_postings,
                counts         = EXCLUDED.counts,
                recorded_at    = now()
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<MonthMentions> findByMonth(String monthLabel, String vocabularyVersion) {
        if (monthLabel == null) {
            return Optional.empty();
        }
        try {
            return jdbcClient.sql(FIND_SQL)
                    .params(monthLabel, vocabularyVersion)
                    .query((rs, rowNum) -> new MonthMentions(
                            rs.getString("month_label"),
                            readCounts(rs.getString("counts")),
                            rs.getInt("total_postings")))
                    .optional();
        } catch (Exception e) {
            log.warn("Tech-demand history read failed for {}: {}", monthLabel, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(MonthMentions month, String vocabularyVersion) {
        if (month == null || month.label() == null) {
            return;
        }
        try {
            jdbcClient.sql(UPSERT_SQL)
                    .params(month.label(), vocabularyVersion, month.total(),
                            objectMapper.writeValueAsString(month.counts()))
                    .update();
            log.info("Tech-demand history: recorded {} ({} postings, vocab {})",
                    month.label(), month.total(), vocabularyVersion);
        } catch (Exception e) {
            log.warn("Tech-demand history write failed for {}: {}", month.label(), e.getMessage());
        }
    }

    private Map<String, Integer> readCounts(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable tech_demand_history.counts payload", e);
        }
    }
}
