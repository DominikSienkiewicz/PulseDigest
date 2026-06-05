package pl.seniordeveloper.pulsedigest;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportCommand;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.command.GenerateMarketReportService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.result.Result;
import pl.seniordeveloper.pulsedigest.shared.util.UuidV7Generator;

/**
 * Entry point of the batch run: kicks off one digest generation and maps its terminal outcome to a
 * process exit code. The pipeline runs synchronously, so no polling is needed — once
 * {@link GenerateMarketReportService#handle} returns, the job is already in a terminal state.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DigestRunner implements ApplicationRunner {

    private final GenerateMarketReportService generateService;
    private final MarketIntelJobTracker jobTracker;

    @Override
    public void run(ApplicationArguments args) {
        String jobId = UuidV7Generator.next().toString();
        log.info("=== PulseDigest pipeline starting (jobId={}) ===", jobId);

        Result<String, MarketIntelError> startResult =
                generateService.handle(new GenerateMarketReportCommand(jobId));

        if (startResult instanceof Result.Failure<String, MarketIntelError> failure) {
            log.error("Pipeline rejected: {}", failure.error().message());
            System.exit(1);
            return;
        }

        // handle() ran the pipeline synchronously — the job is already terminal in the tracker.
        System.exit(resolveExitCode(jobTracker.getJob(jobId).orElse(null)));
    }

    /**
     * Maps the terminal job state to a process exit code: {@code 0} only when the email was actually
     * delivered, {@code 1} for every failure or unexpected non-terminal state.
     */
    int resolveExitCode(ReportJob job) {
        if (job == null) {
            log.error("=== Digest failed: job vanished from tracker after synchronous run ===");
            return 1;
        }
        return switch (job.status()) {
            case DELIVERED -> {
                log.info("=== Digest complete — email sent ===");
                yield 0;
            }
            case EMAIL_FAILED, ERROR -> {
                log.error("=== Digest failed: {} ===", job.error());
                yield 1;
            }
            default -> {
                log.error("=== Digest ended in non-terminal state {} ===", job.status());
                yield 1;
            }
        };
    }
}
