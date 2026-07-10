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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseReaderProfileAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private JdbcClient jdbc;
    private SupabaseReaderProfileAdapter adapter;

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
        jdbc.sql("DELETE FROM reader_profile").update();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        adapter = new SupabaseReaderProfileAdapter(jdbc, objectMapper);
    }

    @Test
    void returnsEmptyBeforeTheReaderHasEverEarnedAProfile() {
        assertThat(adapter.latest()).isEmpty();
    }

    @Test
    void roundTripsAProfileWithItsHypothesesAndEvidence() {
        adapter.save(profile(Instant.parse("2026-07-06T06:00:00Z"), 20));

        assertThat(adapter.latest()).get().satisfies(p -> {
            assertThat(p.voteCount()).isEqualTo(20);
            assertThat(p.hypotheses()).singleElement().satisfies(h -> {
                assertThat(h.statement()).isEqualTo("Chce więcej Javy");
                assertThat(h.evidence()).isEqualTo("12 głosów 👍 w Java/JVM");
                assertThat(h.observedAt()).isEqualTo(LocalDate.of(2026, 7, 6));
            });
        });
    }

    @Test
    void savingIsAppendOnlySoAnEarlierProfileStaysInspectable() {
        // A profile that drifted must remain next to the one that replaced it.
        adapter.save(profile(Instant.parse("2026-07-01T06:00:00Z"), 10));
        adapter.save(profile(Instant.parse("2026-07-08T06:00:00Z"), 30));

        Integer rows = jdbc.sql("SELECT count(*) FROM reader_profile").query(Integer.class).single();

        assertThat(rows).isEqualTo(2);
        assertThat(adapter.latest()).get().satisfies(p -> assertThat(p.voteCount()).isEqualTo(30));
    }

    @Test
    void anUnreadablePayloadYieldsAnEmptyProfileRatherThanAFailedRun() {
        jdbc.sql("INSERT INTO reader_profile (distilled_at, vote_count, profile) VALUES (now(), 5, ?::jsonb)")
                .param("{\"not\":\"an array\"}").update();

        assertThat(adapter.latest()).get().satisfies(p -> assertThat(p.isEmpty()).isTrue());
    }

    private static ReaderProfile profile(Instant distilledAt, int voteCount) {
        return new ReaderProfile(distilledAt, voteCount, List.of(
                new ProfileHypothesis("Chce więcej Javy", "12 głosów 👍 w Java/JVM",
                        LocalDate.of(2026, 7, 6))));
    }
}
