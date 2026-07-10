package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.MonthMentions;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SupabaseTechDemandHistoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private SupabaseTechDemandHistoryAdapter history;

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
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM tech_demand_history").update();
        history = new SupabaseTechDemandHistoryAdapter(jdbc, new ObjectMapper());
    }

    @Test
    void savesAndReadsBackAMonthSnapshot() {
        history.save(new MonthMentions("June 2026", Map.of("java", 120, "rust", 40), 900), "v1");

        Optional<MonthMentions> found = history.findByMonth("June 2026", "v1");

        assertThat(found).get().satisfies(month -> {
            assertThat(month.total()).isEqualTo(900);
            assertThat(month.counts()).containsEntry("java", 120).containsEntry("rust", 40);
        });
    }

    @Test
    void aChangedVocabularyIsADifferentSeriesRatherThanAnOverwrite() {
        // Comparing counts produced by different technology lists would be meaningless.
        history.save(new MonthMentions("June 2026", Map.of("java", 120), 900), "v1");
        history.save(new MonthMentions("June 2026", Map.of("java", 120, "zig", 3), 900), "v2");

        assertThat(history.findByMonth("June 2026", "v1")).get()
                .satisfies(m -> assertThat(m.counts()).hasSize(1));
        assertThat(history.findByMonth("June 2026", "v2")).get()
                .satisfies(m -> assertThat(m.counts()).hasSize(2));
    }

    @Test
    void reSavingTheSameMonthReplacesTheSnapshot() {
        history.save(new MonthMentions("June 2026", Map.of("java", 100), 800), "v1");
        history.save(new MonthMentions("June 2026", Map.of("java", 130), 950), "v1");

        assertThat(history.findByMonth("June 2026", "v1")).get()
                .satisfies(m -> assertThat(m.total()).isEqualTo(950));
    }

    @Test
    void returnsEmptyForAMonthNeverRecorded() {
        assertThat(history.findByMonth("May 2026", "v1")).isEmpty();
        assertThat(history.findByMonth(null, "v1")).isEmpty();
    }
}
