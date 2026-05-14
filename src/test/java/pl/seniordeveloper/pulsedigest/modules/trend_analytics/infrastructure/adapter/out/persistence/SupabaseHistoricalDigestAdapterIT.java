package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.persistence;

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
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseHistoricalDigestAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseHistoricalDigestAdapter adapter;
    private JdbcClient jdbc;

    @BeforeAll
    static void setupDataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        String schema = new String(Objects.requireNonNull(
                SupabaseHistoricalDigestAdapterIT.class.getResourceAsStream("/schema.sql"),
                "schema.sql not found on classpath").readAllBytes(), StandardCharsets.UTF_8);
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(schema);
        }
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM reports").update();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        adapter = new SupabaseHistoricalDigestAdapter(jdbc, objectMapper);
    }

    @Test
    void returnsEmptyWhenNoReports() {
        assertThat(adapter.fetchRecent(7)).isEmpty();
    }

    @Test
    void parsesItemsFromJsonbPayload() {
        Instant now = Instant.now();
        insertRow("job-1", now, """
                {
                  "report": {
                    "items": [
                      {"title":"CVE-1","category":"Security/Privacy","score":9},
                      {"title":"GraalVM","category":"Java/JVM","score":8}
                    ]
                  }
                }
                """);

        List<HistoricalDigest> digests = adapter.fetchRecent(7);

        assertThat(digests).hasSize(1);
        assertThat(digests.getFirst().items()).hasSize(2);
        assertThat(digests.getFirst().items().getFirst().category()).isEqualTo("Security/Privacy");
    }

    @Test
    void filtersOutReportsOlderThanLookbackWindow() {
        Instant now = Instant.now();
        insertRow("old", now.minus(10, ChronoUnit.DAYS), "{\"report\":{\"items\":[]}}");
        insertRow("recent", now.minus(2, ChronoUnit.DAYS), "{\"report\":{\"items\":[]}}");

        List<HistoricalDigest> digests = adapter.fetchRecent(7);

        assertThat(digests).hasSize(1);
    }

    @Test
    void skipsMalformedPayloadGracefully() {
        Instant now = Instant.now();
        insertRow("good", now, "{\"report\":{\"items\":[{\"title\":\"x\",\"category\":\"AI\",\"score\":5}]}}");
        insertRow("broken", now.minus(1, ChronoUnit.DAYS), "{\"unexpected\":\"shape\"}");

        List<HistoricalDigest> digests = adapter.fetchRecent(7);

        assertThat(digests).hasSize(2);
        assertThat(digests).anyMatch(d -> d.items().isEmpty());
        assertThat(digests).anyMatch(d -> d.items().size() == 1);
    }

    private void insertRow(String jobId, Instant generatedAt, String payload) {
        jdbc.sql("INSERT INTO reports (job_id, generated_at, payload) VALUES (?, ?, ?::jsonb)")
                .params(jobId, OffsetDateTime.ofInstant(generatedAt, ZoneOffset.UTC), payload)
                .update();
    }
}
