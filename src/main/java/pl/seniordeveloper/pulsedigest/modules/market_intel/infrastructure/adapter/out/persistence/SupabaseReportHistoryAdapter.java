package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Reads the scored signals of past editions straight out of the {@code reports} JSONB payload.
 * No schema change was needed — every edition has always been stored in full; nothing ever read it.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class SupabaseReportHistoryAdapter implements ReportHistoryPort {

    private static final String COLUMN_GENERATED_AT = "generated_at";

    private static final String RECENT_EDITIONS_SQL = """
            SELECT generated_at,
                   payload->'report'->'signals' AS signals,
                   payload->'fetchReports' AS fetch_reports
            FROM reports
            WHERE generated_at >= ? AND payload->'report'->'signals' IS NOT NULL
            ORDER BY generated_at DESC
            """;

    private static final String ARCHIVED_EDITIONS_SQL = """
            SELECT job_id,
                   generated_at,
                   payload->'report' AS report
            FROM reports
            WHERE generated_at >= ? AND payload->'report' IS NOT NULL
            ORDER BY generated_at DESC
            """;

    private static final String EDITION_BY_ID_SQL = """
            SELECT generated_at,
                   payload->'report'->'signals' AS signals,
                   payload->'fetchReports' AS fetch_reports
            FROM reports
            WHERE job_id = ?
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PastEdition> recentEditions(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<PastEdition> editions = jdbcClient.sql(RECENT_EDITIONS_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> toEdition(
                        rs.getObject(COLUMN_GENERATED_AT, OffsetDateTime.class),
                        rs.getString("signals"),
                        rs.getString("fetch_reports")))
                .list()
                .stream()
                .filter(edition -> !edition.signals().isEmpty())
                .toList();
        log.debug("Report history: {} past edition(s) within {} days", editions.size(), lookbackDays);
        return editions;
    }

    @Override
    public List<ArchivedEdition> archivedEditions(int lookbackDays) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(lookbackDays);
        List<ArchivedEdition> editions = jdbcClient.sql(ARCHIVED_EDITIONS_SQL)
                .param(cutoff)
                .query((rs, rowNum) -> new ArchivedEdition(
                        rs.getString("job_id"),
                        rs.getObject(COLUMN_GENERATED_AT, OffsetDateTime.class).toInstant(),
                        readReport(rs.getString("report"))))
                .list()
                .stream()
                .filter(edition -> edition.report() != null)
                .toList();
        log.info("Archive: {} edition(s) available for publication (last {}d)",
                editions.size(), lookbackDays);
        return editions;
    }

    /** A payload this version cannot read is skipped, not fatal: one old row must not stop a rebuild. */
    private ReportData readReport(String json) {
        try {
            return json == null ? null : objectMapper.readValue(json, ReportData.class);
        } catch (Exception e) {
            log.warn("Archive: skipping an edition whose payload could not be read: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<PastEdition> edition(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql(EDITION_BY_ID_SQL)
                .param(jobId)
                .query((rs, rowNum) -> toEdition(
                        rs.getObject(COLUMN_GENERATED_AT, OffsetDateTime.class),
                        rs.getString("signals"),
                        rs.getString("fetch_reports")))
                .optional();
    }

    // A single unparseable legacy payload must not take the run down: skip that edition and move on.
    private PastEdition toEdition(OffsetDateTime generatedAt, String signalsJson, String fetchReportsJson) {
        List<Signal> signals = List.of();
        try {
            signals = objectMapper.<List<Signal>>readValue(signalsJson, new TypeReference<List<Signal>>() { })
                    .stream()
                    .filter(signal -> !signal.item().correlationKey().isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("Skipping unreadable edition from {}: {}", generatedAt, e.getMessage());
        }
        return new PastEdition(generatedAt.toInstant(), signals, readFetchReports(generatedAt, fetchReportsJson));
    }

    /**
     * Editions written before the archive carried fetch reports have no such column value, and an
     * edition whose reports cannot be read is still a usable edition — the signals are the part the
     * recap and trend memory need. Degrade to an empty list rather than dropping the edition.
     */
    private List<SourceFetchReport> readFetchReports(OffsetDateTime generatedAt, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SourceFetchReport>>() { });
        } catch (Exception e) {
            log.warn("Unreadable fetch reports on the edition from {}: {}", generatedAt, e.getMessage());
            return List.of();
        }
    }
}
