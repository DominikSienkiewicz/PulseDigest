package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

@Slf4j
@RequiredArgsConstructor
@Service
public class GenerateMarketReportService {

    private final MarketIntelJobTracker jobTracker;
    private final GenerateMarketReportProcessor processor;

    /**
     * Tracks the job, runs the pipeline synchronously, and reports what the run actually achieved.
     *
     * <p>{@code process} turns every pipeline failure into a terminal job status rather than an
     * exception, so the job's own status is the outcome — and reading it here is what makes the
     * {@code Result} ADR-0007 mandates carry real information. Until 2026-10-18 this method
     * returned {@code Result.success} unconditionally while {@code DigestRunner} separately
     * re-queried the tracker to find out whether the run had worked; the outcome now travels with
     * the return value, and there is one source of truth for it instead of two.
     *
     * <p>Two guards stood here until 2026-10-17 and neither could act: an in-progress check against
     * a tracker that is always empty on the only call of the run, and a cooldown against an
     * in-memory {@code lastSubmittedAt} that is always null in a process which handles one command
     * and exits. The double-send protection is the workflow's {@code concurrency} group (F16 stays
     * open: there is still no guard at the application level, and there never was one).
     */
    public Result<String, MarketIntelError> handle(GenerateMarketReportCommand command) {
        jobTracker.track(ReportJob.pending(command.jobId()));

        log.info("Running report generation for jobId: {}", command.jobId());
        processor.process(command.jobId());

        return outcome(command.jobId());
    }

    private Result<String, MarketIntelError> outcome(String jobId) {
        return jobTracker.getJob(jobId)
                .map(job -> job.status() == ReportJobStatus.DELIVERED
                        ? Result.<String, MarketIntelError>success(jobId)
                        : Result.<String, MarketIntelError>failure(
                                new MarketIntelError.PipelineFailed(jobId, job.status(), job.error())))
                .orElseGet(() -> Result.failure(new MarketIntelError.JobVanished(jobId)));
    }
}
