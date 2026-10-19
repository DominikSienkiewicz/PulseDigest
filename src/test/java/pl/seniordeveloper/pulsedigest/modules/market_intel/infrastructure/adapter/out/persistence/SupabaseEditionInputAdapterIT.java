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
import org.instancio.Instancio;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N24. The edition input must come back out of the database as the same object the prompt was built
 * from — otherwise replay (N25) would re-run against something subtly different from what shipped,
 * which is worse than not replaying at all.
 */
@Testcontainers
class SupabaseEditionInputAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private JdbcClient jdbc;
    private SupabaseEditionInputAdapter adapter;

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
        jdbc.sql("DELETE FROM edition_inputs").update();
        jdbc.sql("DELETE FROM reports").update();
        adapter = new SupabaseEditionInputAdapter(jdbc, new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    void theStoredInputComesBackAsTheSameResearchResult() {
        insertEdition("job-1");
        ResearchResult original = sampleResearch();

        adapter.save("job-1", original);

        assertThat(adapter.load("job-1")).isPresent().get().satisfies(loaded -> {
            assertThat(loaded.hackerNewsPosts()).isEqualTo(original.hackerNewsPosts());
            assertThat(loaded.sourceFetchReports()).isEqualTo(original.sourceFetchReports());
            assertThat(loaded.collectedAt()).isEqualTo(original.collectedAt());
        });
    }

    @Test
    void anEditionWithoutACapturedInputSimplyHasNone() {
        insertEdition("job-2");

        assertThat(adapter.load("job-2")).isEmpty();
    }

    @Test
    void recapturingTheSameEditionReplacesTheInputRatherThanFailing() {
        insertEdition("job-3");
        ResearchResult first = sampleResearch();
        ResearchResult second = sampleResearch();
        adapter.save("job-3", first);

        adapter.save("job-3", second);

        assertThat(adapter.load("job-3")).isPresent().get()
                .satisfies(loaded -> assertThat(loaded.collectedAt()).isEqualTo(second.collectedAt()));
        assertThat(jdbc.sql("SELECT count(*) FROM edition_inputs").query(Integer.class).single()).isEqualTo(1);
    }

    /**
     * The foreign key is the point: an input without its edition is unreplayable noise, so the
     * schema refuses it rather than letting it accumulate.
     */
    @Test
    void anInputForAnEditionThatDoesNotExistIsNotStored() {
        adapter.save("job-missing", sampleResearch());

        assertThat(jdbc.sql("SELECT count(*) FROM edition_inputs").query(Integer.class).single()).isZero();
    }

    private void insertEdition(String jobId) {
        jdbc.sql("INSERT INTO reports (job_id, generated_at, payload) VALUES (?, now(), '{}'::jsonb)")
                .param(jobId).update();
    }

    /** Generated rather than hand-built: this test is about storage, not about any field's value. */
    private static ResearchResult sampleResearch() {
        return Instancio.create(ResearchResult.class);
    }
}
