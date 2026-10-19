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
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DeliveryProperties;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F16. The guard's whole value is that the decision belongs to the database rather than to the
 * application, so it is worth nothing until it has been run against a real one: {@code ON CONFLICT
 * … DO UPDATE … WHERE} and {@code make_interval} are both Postgres semantics no unit test can check.
 */
@Testcontainers
class SupabaseEditionDeliveryAdapterIT {

    private static final LocalDate EDITION = LocalDate.of(2026, 10, 19);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private JdbcClient jdbc;
    private SupabaseEditionDeliveryAdapter adapter;

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
        jdbc.sql("DELETE FROM edition_deliveries").update();
        adapter = new SupabaseEditionDeliveryAdapter(jdbc, new DeliveryProperties(true, 60));
    }

    @Test
    void theFirstRunToAskOwnsTheSend() {
        assertThat(adapter.claim(EDITION, "job-1")).isTrue();
    }

    @Test
    void aSecondRunIsRefusedWhileTheFirstStillHoldsAFreshClaim() {
        adapter.claim(EDITION, "job-1");

        assertThat(adapter.claim(EDITION, "job-2")).isFalse();
    }

    @Test
    void aDifferentEditionIsNotBlockedByTodays() {
        adapter.claim(EDITION, "job-1");

        assertThat(adapter.claim(EDITION.plusDays(3), "job-2")).isTrue();
    }

    @Test
    void anUnfinishedClaimCanBeTakenOverOnceItGoesStale() {
        // The run that died between claiming and sending. Without a takeover window this edition
        // could never be retried, which would make the guard worse than the problem it solves.
        adapter.claim(EDITION, "job-died");
        age(EDITION, 61);

        assertThat(adapter.claim(EDITION, "job-retry")).isTrue();
        assertThat(jdbc.sql("SELECT job_id FROM edition_deliveries WHERE edition_date = ?")
                .param(EDITION).query(String.class).single()).isEqualTo("job-retry");
    }

    @Test
    void aDeliveredEditionIsNeverTakenOverHoweverOldTheRowIs() {
        // The one guarantee that must not have a timeout: a send that happened cannot un-happen.
        adapter.claim(EDITION, "job-1");
        adapter.markDelivered(EDITION, "job-1");
        age(EDITION, 60 * 24 * 365);

        assertThat(adapter.claim(EDITION, "job-2")).isFalse();
    }

    @Test
    void reportsWhenTheEditionWentOutAndNotBeforeItDid() {
        adapter.claim(EDITION, "job-1");
        assertThat(adapter.deliveredAt(EDITION))
                .as("a claim is not a delivery — this is what keeps a failed send retryable")
                .isEmpty();

        adapter.markDelivered(EDITION, "job-1");

        assertThat(adapter.deliveredAt(EDITION)).isPresent();
    }

    @Test
    void knowsNothingAboutAnEditionNobodyHasClaimed() {
        assertThat(adapter.deliveredAt(EDITION)).isEmpty();
    }

    @Test
    void aRunThatLostItsClaimCannotMarkTheEditionDelivered() {
        adapter.claim(EDITION, "job-died");
        age(EDITION, 61);
        adapter.claim(EDITION, "job-retry");

        adapter.markDelivered(EDITION, "job-died");

        assertThat(adapter.deliveredAt(EDITION))
                .as("the edition belongs to the run that holds the claim now")
                .isEmpty();
    }

    /** Backdates the claim so the takeover window can be exercised without waiting for it. */
    private void age(LocalDate edition, int minutes) {
        jdbc.sql("UPDATE edition_deliveries SET claimed_at = now() - make_interval(mins => ?) "
                        + "WHERE edition_date = ?")
                .params(minutes, edition)
                .update();
    }
}
