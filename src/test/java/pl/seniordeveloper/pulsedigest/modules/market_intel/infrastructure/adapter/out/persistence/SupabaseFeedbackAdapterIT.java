package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseFeedbackAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private JdbcClient jdbc;
    private SupabaseFeedbackAdapter adapter;

    @BeforeAll
    static void setupDataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        String schema = new String(Objects.requireNonNull(
                SupabaseFeedbackAdapterIT.class.getResourceAsStream("/schema.sql"),
                "schema.sql not found on classpath").readAllBytes(), StandardCharsets.UTF_8);
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(schema);
        }
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM feedback").update();
        adapter = new SupabaseFeedbackAdapter(jdbc);
    }

    @Test
    void returnsRecentDownvotedUrlsOnly() {
        insert("https://example.com/a", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/b", "UP", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/c", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));

        Set<String> urls = adapter.downvotedUrls(10);

        assertThat(urls).containsExactlyInAnyOrder("https://example.com/a", "https://example.com/c");
    }

    @Test
    void excludesDownvotesOlderThanLookback() {
        insert("https://example.com/old", "DOWN", Instant.now().minus(40, ChronoUnit.DAYS));

        assertThat(adapter.downvotedUrls(10)).isEmpty();
    }

    @Test
    void returnsEmptySetWhenNoFeedback() {
        assertThat(adapter.downvotedUrls(10)).isEmpty();
    }

    @Test
    void aggregatesNetVotesPerSourceWithinLookback() {
        insert("https://example.com/g1", "GitHub", "UP", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/g2", "GitHub", "UP", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/g3", "GitHub", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/r1", "Reddit", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));
        insert("https://example.com/r2", "Reddit", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));

        Map<String, Integer> net = adapter.netVotesBySource(10);

        assertThat(net).containsEntry("GitHub", 1).containsEntry("Reddit", -2);
    }

    @Test
    void netVotesExcludeVotesOlderThanLookback() {
        insert("https://example.com/old", "GitHub", "DOWN", Instant.now().minus(40, ChronoUnit.DAYS));
        insert("https://example.com/new", "GitHub", "UP", Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(adapter.netVotesBySource(10)).containsExactly(Map.entry("GitHub", 1));
    }

    @Test
    void netVotesEmptyWhenNoFeedback() {
        assertThat(adapter.netVotesBySource(10)).isEmpty();
    }

    private void insert(String url, String vote, Instant createdAt) {
        insert(url, "GitHub", vote, createdAt);
    }

    private void insert(String url, String source, String vote, Instant createdAt) {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, created_at) VALUES (?, ?, ?, ?)")
                .params(url, source, vote, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }
}
