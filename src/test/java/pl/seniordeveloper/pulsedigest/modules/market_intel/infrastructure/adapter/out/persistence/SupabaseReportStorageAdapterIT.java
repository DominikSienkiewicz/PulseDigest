package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseReportStorageAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseReportStorageAdapter adapter;
    private JdbcClient jdbc;

    @BeforeAll
    static void setupDataSource() throws Exception {
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
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        adapter = new SupabaseReportStorageAdapter(jdbc, objectMapper);
    }

    @Test
    void getLatestReturnsEmptyWhenNoRows() {
        assertThat(adapter.getLatest()).isEmpty();
    }

    @Test
    void savesAndRetrievesLatestReport() {
        adapter.save(sampleReport("job-1", Instant.parse("2026-05-04T10:00:00Z")));

        Optional<PersistedReport> latest = adapter.getLatest();
        assertThat(latest).isPresent();
        assertThat(latest.get().jobId()).isEqualTo("job-1");
    }

    @Test
    void getLatestReturnsMostRecentByGeneratedAt() {
        adapter.save(sampleReport("older", Instant.parse("2026-05-01T10:00:00Z")));
        adapter.save(sampleReport("newer", Instant.parse("2026-05-04T10:00:00Z")));

        assertThat(adapter.getLatest())
                .isPresent()
                .get()
                .extracting(PersistedReport::jobId)
                .isEqualTo("newer");
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

    private static PersistedReport sampleReport(String jobId, Instant generatedAt) {
        ReportData data = new ReportData(
                "preview", "editorial", List.of("i1"),
                List.of(new DigestItem("title", "https://x", "Twitter",
                        "AI/LLM", "RELEASE", 8, 50, "sum", "why"))
        );
        return new PersistedReport(data, jobId, generatedAt, 10, 0, 0);
    }
}
