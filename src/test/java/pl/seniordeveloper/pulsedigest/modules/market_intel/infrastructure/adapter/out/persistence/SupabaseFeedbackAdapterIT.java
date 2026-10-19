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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;

import java.util.Map;
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

        TestSchema.migrate(dataSource);
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

    @Test
    void readsBackWhatTheReaderSavedAndHasNotVotedOnSince() {
        insert("https://example.com/kept", "SAVE", Instant.now().minus(4, ChronoUnit.DAYS));

        assertThat(adapter.savedItems(30))
                .extracting(SavedItem::url)
                .containsExactly("https://example.com/kept");
    }

    @Test
    void dropsASavedItemOnceTheReaderCameBackAndVotedOnIt() {
        // A later 👍 or 👎 is the closest thing this schema has to evidence the reader dealt with it.
        // Without this the shelf would keep nagging about items that are demonstrably finished.
        insertInEdition("https://example.com/liked", "SAVE", "2026-10-12");
        insertInEdition("https://example.com/liked", "UP", "2026-10-15");
        insertInEdition("https://example.com/disliked", "SAVE", "2026-10-12");
        insertInEdition("https://example.com/disliked", "DOWN", "2026-10-15");
        insertInEdition("https://example.com/still-waiting", "SAVE", "2026-10-12");

        assertThat(adapter.savedItems(30))
                .extracting(SavedItem::url)
                .containsExactly("https://example.com/still-waiting");
    }

    @Test
    void keepsTheEarliestSaveWhenAnItemWasSavedOutOfTwoEditions() {
        // How long something has been sitting there is the point of the reminder, so the first save
        // wins — and the row appears once, not twice.
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition, created_at) VALUES (?, ?, ?, ?, ?)")
                .params("https://example.com/twice", "GitHub", "SAVE", "2026-10-01",
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(18))
                .update();
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition, created_at) VALUES (?, ?, ?, ?, ?)")
                .params("https://example.com/twice", "GitHub", "SAVE", "2026-10-15",
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(4))
                .update();

        assertThat(adapter.savedItems(30)).hasSize(1);
        assertThat(adapter.savedItems(30).getFirst().savedAt())
                .isBefore(Instant.now().minus(17, ChronoUnit.DAYS));
    }

    @Test
    void returnsSavedItemsEarliestFirst() {
        insert("https://example.com/newer", "SAVE", Instant.now().minus(2, ChronoUnit.DAYS));
        insert("https://example.com/older", "SAVE", Instant.now().minus(20, ChronoUnit.DAYS));

        assertThat(adapter.savedItems(30))
                .extracting(SavedItem::url)
                .containsExactly("https://example.com/older", "https://example.com/newer");
    }

    @Test
    void forgetsASaveOnceItFallsOutOfTheWindow() {
        insert("https://example.com/ancient", "SAVE", Instant.now().minus(40, ChronoUnit.DAYS));

        assertThat(adapter.savedItems(30)).isEmpty();
    }

    @Test
    void carriesTheSourceAndCategoryTheMailSentWithTheSave() {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, category) VALUES (?, ?, ?, ?)")
                .params("https://example.com/a", "Hacker News", "SAVE", "Java/JVM").update();

        SavedItem saved = adapter.savedItems(30).getFirst();

        assertThat(saved.source()).isEqualTo("Hacker News");
        assertThat(saved.category()).isEqualTo("Java/JVM");
        assertThat(saved.title()).as("the receiver never sees a title").isNull();
    }

    private void insertInEdition(String url, String vote, String edition) {
        jdbc.sql("INSERT INTO feedback (item_url, source, vote, edition) VALUES (?, ?, ?, ?)")
                .params(url, "GitHub", vote, edition).update();
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
