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
import pl.seniordeveloper.pulsedigest.shared.result.Result;
import pl.seniordeveloper.pulsedigest.shared.util.UuidV7Generator;

@Slf4j
@RequiredArgsConstructor
@Component
public class DigestRunner implements ApplicationRunner {

    private static final int MAX_WAIT_SECONDS = 600;

    private final GenerateMarketReportService generateService;
    private final MarketIntelJobTracker jobTracker;

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        String jobId = UuidV7Generator.next().toString();
        log.info("=== PulseDigest pipeline starting (jobId={}) ===", jobId);

        Result<String, MarketIntelError> startResult =
                generateService.handle(new GenerateMarketReportCommand(jobId));

        if (startResult instanceof Result.Failure<String, MarketIntelError> failure) {
            log.error("Pipeline rejected: {}", failure.error().message());
            System.exit(1);
            return;
        }

        log.info("Pipeline accepted — waiting for completion (max {}s)...", MAX_WAIT_SECONDS);

        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            Thread.sleep(1000);
            var maybeJob = jobTracker.getJob(jobId);
            if (maybeJob.isEmpty()) {
                continue;
            }
            var job = maybeJob.get();
            switch (job.status()) {
                case DELIVERED -> {
                    log.info("=== Digest complete — email sent ===");
                    System.exit(0);
                }
                case EMAIL_FAILED, ERROR -> {
                    log.error("=== Digest failed: {} ===", job.error());
                    System.exit(1);
                }
                default -> { /* still in progress */ }
            }
        }

        log.error("Digest timed out after {}s", MAX_WAIT_SECONDS);
        System.exit(1);
    }
}
