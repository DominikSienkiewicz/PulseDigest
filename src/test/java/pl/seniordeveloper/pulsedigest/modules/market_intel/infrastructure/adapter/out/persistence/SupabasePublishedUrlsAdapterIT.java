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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabasePublishedUrlsAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseReportStorageAdapter storage;
    private SupabasePublishedUrlsAdapter publishedUrls;

    @BeforeAll
    static void setupDataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        String schema = new String(Objects.requireNonNull(
                SupabasePublishedUrlsAdapterIT.class.getResourceAsStream("/schema.sql"),
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
        publishedUrls = new SupabasePublishedUrlsAdapter(jdbc);
    }

    @Test
    void returnsItemUrlsFromEditionsWithinLookback() {
        storage.save(report("recent", Instant.now().minus(1, ChronoUnit.DAYS),
                "https://example.com/a", "https://example.com/b"));

        Set<String> urls = publishedUrls.recentlyPublishedUrls(10);

        assertThat(urls).containsExactlyInAnyOrder("https://example.com/a", "https://example.com/b");
    }

    @Test
    void excludesEditionsOlderThanLookback() {
        storage.save(report("old", Instant.now().minus(30, ChronoUnit.DAYS), "https://example.com/old"));

        Set<String> urls = publishedUrls.recentlyPublishedUrls(10);

        assertThat(urls).isEmpty();
    }

    @Test
    void returnsEmptySetWhenNoHistory() {
        assertThat(publishedUrls.recentlyPublishedUrls(10)).isEmpty();
    }

    private static PersistedReport report(String jobId, Instant generatedAt, String... urls) {
        List<DigestItem> items = Arrays.stream(urls)
                .map(u -> new DigestItem("title", u, "GitHub", "Java", "RELEASE", 8, 10, "sum", "why"))
                .toList();
        ReportData data = new ReportData("preview", "editorial", List.of("insight"), items);
        return new PersistedReport(data, jobId, generatedAt, items.size(), 0, 0);
    }
}
