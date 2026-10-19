package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence.SupabaseFeedbackAdapter;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import javax.sql.DataSource;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F6. The feedback loop's one production contract that lives outside this repository.
 *
 * <p>The digest emits {@code vote=up} in lowercase. The schema accepts only
 * {@code CHECK (vote IN ('UP','DOWN','SAVE'))}. Both reading queries match on the uppercase form. Between
 * them sits an external receiver — a function the author deploys, whose source is not here — and the
 * whole loop depends on it uppercasing the value in transit. That obligation was written nowhere and
 * tested nowhere: rows were observed arriving, which proved the deployed receiver does it today and
 * nothing more. A redeploy without that one call would leave every vote either rejected by the
 * constraint or silently uncounted by the queries, with no failing build and no alert.
 *
 * <p>This test makes the obligation explicit and executable. It starts from the link the mail
 * actually renders rather than from a constant, so it fails if any of the three sides moves: the
 * emitted case, the constraint, or the case the adapter's SQL matches on.
 *
 * <p>What it deliberately does not do is assert the receiver's own behaviour — there is no receiver
 * here to assert against. It pins the contract the receiver must satisfy, which is the part this
 * repository can own.
 */
@Testcontainers
class FeedbackVoteContractIT {

    // The mail encodes the separator, so links read `...?url=…&amp;vote=up&amp;source=…`.
    private static final Pattern VOTE_PARAM = Pattern.compile("(?:\\?|&amp;|&)vote=([^&\"]+)");
    private static final String RECEIVER_URL = "https://receiver.example.invalid/feedback";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private JdbcClient jdbc;
    private SupabaseFeedbackAdapter adapter;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM feedback").update();
        adapter = new SupabaseFeedbackAdapter(jdbc);
    }

    @Test
    void theMailEmitsLowercaseVotesThatTheSchemaRefusesUntilTheReceiverNormalisesThem() {
        List<String> emitted = votesRenderedInTheMail();

        assertThat(emitted)
                .as("the mail's own vote values, as a reader's click would send them")
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(List.of("up", "down", "save"));

        for (String raw : emitted) {
            assertThatThrownBy(() -> insert("https://example.com/" + raw, raw))
                    .as("the schema must refuse the raw link value %s — if it ever stops refusing it, "
                            + "lowercase rows start accumulating that neither reading query counts", raw)
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(insert("https://example.com/" + raw, raw.toUpperCase(java.util.Locale.ROOT)))
                    .as("the normalised form is what the receiver owes the database")
                    .isEqualTo(1);
        }
    }

    @Test
    void onlyTheNormalisedRowsAreVisibleToTheQueriesThatDriveScoring() {
        insert("https://example.com/liked", "UP");
        insert("https://example.com/disliked", "DOWN");

        assertThat(adapter.downvotedUrls(30)).containsExactly("https://example.com/disliked");
        assertThat(adapter.netVotesBySource(30)).containsEntry("GitHub", 0);
    }

    @Test
    void aSavedItemIsNeitherAPreferenceNorAPenalty() {
        // N05. The arithmetic this pins is the whole reason the third link is safe to ship: the
        // previous `ELSE -1` would have read every bookmark as a down-vote against its source.
        insert("https://example.com/for-later", "SAVE");

        assertThat(adapter.netVotesBySource(30)).containsEntry("GitHub", 0);
        assertThat(adapter.downvotedUrls(30)).isEmpty();
        assertThat(adapter.upvotedUrls(30))
                .as("a bookmark is not a reader confirming the item earned its slot")
                .isEmpty();
    }

    @Test
    void aSavedItemCancelsNothingWhenItSharesASourceWithRealVotes() {
        insert("https://example.com/liked", "UP");
        insert("https://example.com/for-later", "SAVE");

        assertThat(adapter.netVotesBySource(30)).containsEntry("GitHub", 1);
    }

    /** Renders the committed fixture edition and pulls every vote value out of its feedback links. */
    private static List<String> votesRenderedInTheMail() {
        DigestEditionFixture fixture = DigestEditionFixture.load();
        String html = new ReportEmailBuilder(new FeedbackProperties(true, 30, RECEIVER_URL, ""),
                new WatchlistProperties(true, fixture.watchlistTechnologies()), new ThinWeekProperties(true, 3),
                new SectionsPolicy(java.util.Set.of()))
                .buildHtml(fixture.report(), fixture.research());

        Matcher m = VOTE_PARAM.matcher(html);
        return m.results().map(r -> r.group(1)).distinct().toList();
    }

    private int insert(String url, String vote) {
        return jdbc.sql("INSERT INTO feedback (item_url, source, vote) VALUES (?, ?, ?)")
                .params(url, "GitHub", vote)
                .update();
    }
}
