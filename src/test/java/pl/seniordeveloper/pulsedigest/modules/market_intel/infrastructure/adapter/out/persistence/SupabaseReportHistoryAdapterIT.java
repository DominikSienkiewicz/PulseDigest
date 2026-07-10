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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseReportHistoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseReportStorageAdapter storage;
    private SupabaseReportHistoryAdapter history;

    @BeforeAll
    static void setupDataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        String schema = new String(Objects.requireNonNull(
                SupabaseReportHistoryAdapterIT.class.getResourceAsStream("/schema.sql"),
                "schema.sql not found on classpath").readAllBytes(), StandardCharsets.UTF_8);
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(schema);
        }
    }

    @BeforeEach
    void setUp() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM reports").update();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        storage = new SupabaseReportStorageAdapter(jdbc, objectMapper);
        history = new SupabaseReportHistoryAdapter(jdbc, objectMapper);
    }

    @Test
    void readsScoredTopicsOfEachEditionNewestFirst() {
        storage.save(edition("older", Instant.now().minus(4, ChronoUnit.DAYS), "mcp", SignalRank.MODERATE));
        storage.save(edition("newer", Instant.now().minus(1, ChronoUnit.DAYS), "mcp", SignalRank.CRITICAL));

        List<PastEdition> editions = history.recentEditions(21);

        assertThat(editions).hasSize(2);
        assertThat(editions.get(0).signals()).extracting(Signal::rank).containsExactly(SignalRank.CRITICAL);
        assertThat(editions.get(1).signals()).extracting(Signal::rank).containsExactly(SignalRank.MODERATE);
        assertThat(editions.get(0).carries("mcp")).isTrue();
    }

    @Test
    void excludesEditionsOlderThanLookback() {
        storage.save(edition("ancient", Instant.now().minus(40, ChronoUnit.DAYS), "mcp", SignalRank.CRITICAL));

        assertThat(history.recentEditions(21)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenNoHistory() {
        assertThat(history.recentEditions(21)).isEmpty();
    }

    @Test
    void legacyEditionsWithoutTopicKeyFallBackToCategory() {
        // Editions written before topic_key existed must still be matchable, not silently skipped.
        DigestItem legacy = new DigestItem("Old story", "https://example.com/old", "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "sum", null);
        ReportData data = new ReportData("preview", "editorial", List.of("insight"), List.of(legacy),
                List.of(new Signal(legacy, SignalRank.STRONG, 90, List.of(SourceDomain.CODE))));
        storage.save(new PersistedReport(data, "legacy", Instant.now().minus(1, ChronoUnit.DAYS), 1, 0, 0));

        List<PastEdition> editions = history.recentEditions(21);

        assertThat(editions).singleElement()
                .satisfies(e -> assertThat(e.carries("ai/llm")).isTrue());
    }

    private static PersistedReport edition(String jobId, Instant generatedAt, String topicKey, SignalRank rank) {
        DigestItem item = new DigestItem("Title", "https://example.com/" + topicKey, "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "sum", null, topicKey);
        ReportData data = new ReportData("preview", "editorial", List.of("insight"), List.of(item),
                List.of(new Signal(item, rank, 120, List.of(SourceDomain.CODE))));
        return new PersistedReport(data, jobId, generatedAt, 1, 0, 0);
    }
}
