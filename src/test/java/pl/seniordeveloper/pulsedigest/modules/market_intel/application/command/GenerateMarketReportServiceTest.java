package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GenerateMarketReportServiceTest {

    private final MarketIntelJobTracker jobTracker = new MarketIntelJobTracker();
    private final GenerateMarketReportProcessor processor = mock(GenerateMarketReportProcessor.class);
    private final GenerateMarketReportService service =
            new GenerateMarketReportService(jobTracker, processor);

    @Test
    void aDeliveredRunSucceedsCarryingItsJobId() {
        processorLeavesJob("job-1", ReportJob::delivered);

        Result<String, MarketIntelError> result = service.handle(new GenerateMarketReportCommand("job-1"));

        assertThat(result).isEqualTo(Result.success("job-1"));
        verify(processor).process("job-1");
    }

    @Test
    void aRunThatEndedInErrorFailsWithItsStatusAndDetail() {
        processorLeavesJob("job-2", job -> job.error("every source returned nothing"));

        Result<String, MarketIntelError> result = service.handle(new GenerateMarketReportCommand("job-2"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(
                result.getError()).isEqualTo(new MarketIntelError.PipelineFailed(
                        "job-2", ReportJobStatus.ERROR, "every source returned nothing"));
    }

    /**
     * The pipeline can leave a job short of any terminal state — a crash between stages, a step that
     * returns without recording an outcome. That is a failed run, not a successful one: reporting it
     * as success would hand the batch an exit code of zero on a digest that never went out.
     */
    @Test
    void aRunLeftInANonTerminalStateFails() {
        processorLeavesJob("job-3", ReportJob::inProgress);

        Result<String, MarketIntelError> result = service.handle(new GenerateMarketReportCommand("job-3"));

        assertThat(result.getError()).isEqualTo(new MarketIntelError.PipelineFailed("job-3", ReportJobStatus.IN_PROGRESS, null));
    }

    /**
     * The tracker must hold the job before {@code process} runs, because the processor's first act is
     * to read it back and abort when it is missing. Asserting the order keeps the two from being
     * reordered into a run that silently does nothing.
     */
    @Test
    void theProcessorCanAlreadySeeTheJobItIsAskedToProcess() {
        doAnswer(invocation -> {
            assertThat(jobTracker.getJob("job-4")).isPresent();
            return null;
        }).when(processor).process("job-4");

        service.handle(new GenerateMarketReportCommand("job-4"));

        verify(processor).process("job-4");
    }

    /** Stands in for the real processor, which drives the tracked job to a terminal state. */
    private void processorLeavesJob(String jobId, UnaryOperator<ReportJob> transition) {
        doAnswer(invocation -> {
            jobTracker.track(transition.apply(jobTracker.getJob(jobId).orElseThrow()));
            return null;
        }).when(processor).process(jobId);
    }
}
