package pl.seniordeveloper.pulsedigest;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportProcessor;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the production Spring context actually assembles: every adapter, policy and
 * {@code @ConfigurationProperties} binding, against a real Postgres carrying the real migration.
 *
 * <p>It replaces a {@code @Disabled} {@code contextLoads()} test whose stated reason was "requires
 * real API keys". That reason was never quite true, and the cost of believing it was a repository
 * with no gate at all on whether its own context boots — the same defect the project calls out in
 * production code (F5: a gate that cannot fire), only quieter, because a disabled test still
 * reports as a test. Validation needs values, not *valid* values: four {@code @NotBlank} bindings
 * (the X bearer token and the three Resend fields) are all that stood in the way, and obvious
 * dummies satisfy them. The OpenAI key joined that list on 2026-10-18: it used to be read with
 * {@code @Value} and default to blank, so a run without it failed at the first LLM call instead of
 * at startup; it is now {@code openai.api-key} with {@code @NotBlank}, like every other
 * credential.
 *
 * <p><b>Why the runner is mocked.</b> {@code @SpringBootTest} does invoke {@code ApplicationRunner}
 * beans — verified by experiment, not assumed. The real {@link DigestRunner} would therefore run the
 * whole digest against these dummy credentials and then call {@code System.exit}, ending the JVM
 * mid-suite. Substituting it keeps every other bean real; {@code DigestRunner}'s own two-argument
 * injection is covered by {@code DigestRunnerTest}, which constructs it directly.
 *
 * <p>No outbound call is made: the adapters are constructed, never invoked.
 */
@Testcontainers
@SpringBootTest(properties = {
        "openai.api-key=test-openai-key",
        "twitter.bearer-token=test-bearer-token",
        "report.email.resend-api-key=test-resend-key",
        "report.email.from=digest@example.invalid",
        "report.email.to=reader@example.invalid"
})
class PulseDigestContextIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Applies the production migration before the context is built. This runs first: JUnit invokes
     * extension {@code beforeAll} callbacks — which is where Testcontainers starts the container —
     * ahead of a user {@code @BeforeAll}, and Spring builds the context later still, when it injects
     * the test instance.
     *
     * <p>It matters that the schema is in place first, and the reason is easy to miss:
     * {@code MarketIntelBootstrap} reads the latest edition in a {@code @PostConstruct} and swallows
     * any failure at WARN, so a missing table would not fail this test — it would quietly reduce it
     * to proving less than it looks like it proves. {@code theContextTalksToTheMigratedSchema} is
     * what keeps that honest.
     *
     * <p>Same Flyway call as {@code TestSchema} in the persistence ITs, inlined rather than reused:
     * that helper is package-private to the persistence adapter package, and importing it from the
     * application root would couple this test to an adapter's test internals. The container's own
     * {@code withInitScript} would be shorter still, but the Testcontainers build in use is missing
     * the shaded {@code commons-io} it needs and throws {@code NoClassDefFoundError}.
     */
    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @MockitoBean
    private DigestRunner digestRunner;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Test
    void thePipelineBeanGraphWires() {
        assertThat(context.getBean(GenerateMarketReportService.class)).isNotNull();
        assertThat(context.getBean(GenerateMarketReportProcessor.class)).isNotNull();
        assertThat(context.getBean(MarketIntelJobTracker.class)).isNotNull();
    }

    @Test
    void everyOutboundPortHasExactlyOneAdapter() {
        assertThat(context.getBeanNamesForType(LlmSynthesisPort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ReportStoragePort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(EmailDeliveryPort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FeedbackPort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ReportHistoryPort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(EditionArchivePort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(RunSummaryPort.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(EditionInputPort.class)).hasSize(1);
    }

    @Test
    void theNarrowPoliciesAreBuiltFromConfiguration() {
        assertThat(context.getBean(ResearchPolicy.class)).isNotNull();
        assertThat(context.getBean(FeedbackNudgePolicy.class)).isNotNull();
        assertThat(context.getBean(ReportHistoryPolicy.class)).isNotNull();
        assertThat(context.getBean(ReaderProfilePolicy.class)).isNotNull();
    }

    /**
     * The context's own {@code DataSource} reaches the container and finds the migrated schema — so
     * {@code @ServiceConnection} really did override the blank {@code spring.datasource.*} values
     * from {@code application.yaml}, and the migration really did land before anything read it.
     */
    @Test
    void theContextTalksToTheMigratedSchema() {
        Long reports = JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM reports")
                .query(Long.class)
                .single();

        assertThat(reports).isZero();
    }

    /**
     * Pins what {@code V2} did, and in doing so proves {@code V2} ran at all: {@code V1} creates
     * {@code tech_demand_history}, so this assertion can only hold if the second migration was
     * applied on top. Nothing else in the suite distinguishes "both migrations applied" from "only
     * the first" — a V2 that silently failed to run would otherwise look exactly like success.
     */
    @Test
    void theOrphanedTechDemandTableIsGoneAndTheRestOfTheSchemaIsNot() {
        JdbcClient jdbc = JdbcClient.create(dataSource);

        assertThat(tableNames(jdbc)).contains("reports", "feedback", "reader_profile")
                .contains("edition_inputs")
                .doesNotContain("tech_demand_history");
    }

    private static java.util.List<String> tableNames(JdbcClient jdbc) {
        return jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class)
                .list();
    }

    /**
     * Guards the F5b removal structurally. {@code @EnableScheduling} is gone because nothing it
     * started could fire in a process that exits in minutes; if someone restores the annotation,
     * Spring registers this post-processor and this test says so.
     */
    @Test
    void noSchedulingInfrastructureIsRegistered() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }
}
