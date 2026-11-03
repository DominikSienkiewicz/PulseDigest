package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseReportStorageAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseReportStorageAdapter adapter;
    private JdbcClient jdbc;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        TestSchema.migrate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM reports").update();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        adapter = new SupabaseReportStorageAdapter(jdbc, objectMapper);
    }

    @Test
    void upsertReplacesExistingRowForSameJobId() {
        adapter.save(sampleReport("job-x", Instant.parse("2026-05-04T10:00:00Z")));
        adapter.save(sampleReport("job-x", Instant.parse("2026-05-04T11:00:00Z")));

        Integer rowCount = jdbc.sql("SELECT COUNT(*) FROM reports WHERE job_id = 'job-x'")
                .query(Integer.class)
                .single();
        assertThat(rowCount).isEqualTo(1);
    }

    /**
     * The stored payload must be complete, not merely accepted by Postgres. This asserted the same
     * thing through {@code adapter.getLatest()} until 2026-10-18; with that read path removed as
     * caller-less, the payload is read straight out of the column instead, so the test keeps its
     * subject — the write — rather than following the method that happened to verify it.
     *
     * <p>Nothing in production deserialises a whole {@code PersistedReport} back any more. What does
     * depend on this payload is its *inside*: the dedup reads {@code payload->'report'->'items'} and
     * trend memory reads {@code ->'signals'}, both with Postgres JSON operators. Hence the second
     * assertion, on the path those consumers actually walk.
     */
    @Test
    void theStoredPayloadCarriesPerSourceFetchReportsAndTheShapeItsConsumersQuery() throws Exception {
        SourceFetchReport quotaFailure = SourceFetchReport.failed("Twitter", 120L, "429 Too Many Requests");
        PersistedReport withFetchReports = new PersistedReport(
                sampleReport("job-fetch", Instant.parse("2026-05-04T10:00:00Z")).report(),
                "job-fetch", Instant.parse("2026-05-04T10:00:00Z"), 10, 0, 0,
                List.of(quotaFailure, SourceFetchReport.success("HackerNews", 5, 80L))
        );

        adapter.save(withFetchReports);

        PersistedReport stored = objectMapper.readValue(payloadOf("job-fetch"), PersistedReport.class);
        assertThat(stored.fetchReports())
                .isEqualTo(List.of(quotaFailure, SourceFetchReport.success("HackerNews", 5, 80L)));

        String firstItemUrl = jdbc.sql("SELECT item->>'url' FROM reports, jsonb_array_elements(payload->'report'->'items') AS item "
                                + "WHERE job_id = 'job-fetch' LIMIT 1")
                .query(String.class)
                .single();
        assertThat(firstItemUrl).isEqualTo("https://x");
    }

    private String payloadOf(String jobId) {
        return jdbc.sql("SELECT payload FROM reports WHERE job_id = ?")
                .param(jobId)
                .query(String.class)
                .single();
    }

    private static PersistedReport sampleReport(String jobId, Instant generatedAt) {
        ReportData data = new ReportData(
                "preview", "editorial", List.of("i1"),
                List.of(TestModels.digestItem("title", "https://x", "Twitter",
                        "AI/LLM", "RELEASE", 8, 50, "sum", "why"))
        );
        return TestModels.persistedReport(data, jobId, generatedAt, 10, 0, 0);
    }
}
