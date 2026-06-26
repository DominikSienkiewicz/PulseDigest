package pl.seniordeveloper.pulsedigest;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DigestRunnerTest {

    private final DigestRunner runner = new DigestRunner(
            mock(GenerateMarketReportService.class), new MarketIntelJobTracker());

    @Test
    void deliveredJobMapsToExitCodeZero() {
        ReportJob job = ReportJob.done("j1", emptyReport(), Instant.now());

        assertThat(runner.resolveExitCode(job)).isZero();
    }

    @Test
    void emailFailedJobMapsToExitCodeOne() {
        ReportJob job = ReportJob.pending("j2").emailFailed("resend unavailable");

        assertThat(runner.resolveExitCode(job)).isEqualTo(1);
    }

    @Test
    void errorJobMapsToExitCodeOne() {
        ReportJob job = ReportJob.pending("j3").error("boom");

        assertThat(runner.resolveExitCode(job)).isEqualTo(1);
    }

    @Test
    void nonTerminalJobMapsToExitCodeOne() {
        ReportJob job = ReportJob.pending("j4").inProgress();

        assertThat(runner.resolveExitCode(job)).isEqualTo(1);
    }

    @Test
    void missingJobMapsToExitCodeOne() {
        assertThat(runner.resolveExitCode(null)).isEqualTo(1);
    }

    private static ReportData emptyReport() {
        return new ReportData(null, null, null, null, null);
    }
}
