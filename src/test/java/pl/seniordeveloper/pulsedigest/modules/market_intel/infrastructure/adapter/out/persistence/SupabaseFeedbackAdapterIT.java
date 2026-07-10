package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void aggregatesNetVotesPerCategoryCaseInsensitively() {
        insertCategorized("https://example.com/a", "UP", "AI/LLM");
        insertCategorized("https://example.com/b", "UP", "ai/llm");
        insertCategorized("https://example.com/c", "DOWN", "Research");

        Map<String, Integer> byCategory = adapter.netVotesByCategory(10);

        assertThat(byCategory).containsEntry("ai/llm", 2).containsEntry("research", -1);
    }

    @Test
    void rowsWithoutACategoryAreExcludedRatherThanBucketedTogether() {
        // Written by a receiver that predates the parameter — degrade to an empty map, not a wrong one.
        insert("https://example.com/a", "DOWN", Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(adapter.netVotesByCategory(10)).isEmpty();
    }

    @Test
    void categoryVotesRespectTheLookbackWindow() {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, category, created_at) VALUES (?, ?, ?, ?, ?)")
                .params("https://example.com/old", "GitHub", "DOWN", "Research",
                        java.time.OffsetDateTime.now().minusDays(40))
                .update();

        assertThat(adapter.netVotesByCategory(10)).isEmpty();
    }

    private void insertCategorized(String url, String vote, String category) {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, category) VALUES (?, ?, ?, ?)")
                .params(url, "GitHub", vote, category).update();
    }

    @Test
    void anItemCanOnlyBeVotedOnceWithinAnEdition() {
        // A mail scanner prefetching the link — or fetching both 👍 and 👎 — must not amplify a vote.
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition) VALUES (?, ?, ?, ?)")
                .params("https://example.com/a", "GitHub", "UP", "2026-07-10").update();

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition) VALUES (?, ?, ?, ?)")
                .params("https://example.com/a", "GitHub", "DOWN", "2026-07-10").update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theSameItemCanBeVotedAgainInALaterEdition() {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition) VALUES (?, ?, ?, ?)")
                .params("https://example.com/a", "GitHub", "UP", "2026-07-10").update();

        int rows = jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition) VALUES (?, ?, ?, ?)")
                .params("https://example.com/a", "GitHub", "DOWN", "2026-07-13").update();

        assertThat(rows).isEqualTo(1);
    }

    @Test
    void rowsWrittenByAReceiverThatDoesNotYetSendTheEditionAreNotConstrained() {
        // Rolling out the column must not break the receiver that is live today.
        jdbc.sql("INSERT INTO feedback (item_url, source, vote) VALUES (?, ?, ?)")
                .params("https://example.com/a", "GitHub", "UP").update();

        int rows = jdbc.sql("INSERT INTO feedback (item_url, source, vote) VALUES (?, ?, ?)")
                .params("https://example.com/a", "GitHub", "DOWN").update();

        assertThat(rows).isEqualTo(1);
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
