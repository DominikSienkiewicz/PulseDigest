package pl.seniordeveloper.pulsedigest;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.RetrospectiveService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceDiscoveryService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RetrospectivePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ArchivePublishService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.DryRunService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReplayService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics.MetricsLogger;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DigestRunnerTest {

    private final MetricsLogger metricsLogger = mock(MetricsLogger.class);
    private final DigestRunner runner = new DigestRunner(mock(GenerateMarketReportService.class),
            mock(RunSummaryPort.class),
            mock(ReplayService.class),
            metricsLogger,
            mock(RetrospectiveService.class),
            mock(EmailDeliveryPort.class),
            mock(SourceDiscoveryService.class),
            new RetrospectivePolicy(90, 30),
            mock(DryRunService.class),
            mock(ArchivePublishService.class));

    /**
     * N43. The snapshot every cost and summary figure is read from must be written while logging is
     * unquestionably alive, not from a JVM shutdown hook racing the logging system's own. Asserting
     * it here is what makes that guarantee hold: the exit-code mapping is the last thing the runner
     * does before {@code System.exit}, so a dump that happens here happens.
     */
    @Test
    void dumpsTheMetricsSnapshotBeforeExitingRatherThanFromAShutdownHook() {
        runner.exitCode(Result.success("j0"));

        verify(metricsLogger).dump();
    }

    @Test
    void aDeliveredRunExitsZero() {
        assertThat(runner.exitCode(Result.success("j1"))).isZero();
    }

    @Test
    void anEmailFailureExitsOne() {
        assertThat(runner.exitCode(failed("j2", ReportJobStatus.EMAIL_FAILED, "resend unavailable"))).isEqualTo(1);
    }

    @Test
    void anErroredRunExitsOne() {
        assertThat(runner.exitCode(failed("j3", ReportJobStatus.ERROR, "boom"))).isEqualTo(1);
    }

    /**
     * A run that never reached a terminal state is a failure, not a success. Exit {@code 0} here
     * would tell GitHub Actions the digest went out when it did not, and the workflow's failure
     * alert hangs off exactly this code.
     */
    @Test
    void aRunLeftInANonTerminalStateExitsOne() {
        assertThat(runner.exitCode(failed("j4", ReportJobStatus.IN_PROGRESS, null))).isEqualTo(1);
    }

    @Test
    void aVanishedJobExitsOne() {
        assertThat(runner.exitCode(Result.failure(new MarketIntelError.JobVanished("j5")))).isEqualTo(1);
    }

    private static Result<String, MarketIntelError> failed(String jobId, ReportJobStatus status, String detail) {
        return Result.failure(new MarketIntelError.PipelineFailed(jobId, status, detail));
    }
}
