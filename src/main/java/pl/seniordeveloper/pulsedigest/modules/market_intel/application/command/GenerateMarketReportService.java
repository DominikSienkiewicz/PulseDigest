package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class GenerateMarketReportService {

    private final MarketIntelJobTracker jobTracker;
    private final GenerateMarketReportProcessor processor;
    private final ReportProperties reportProperties;

    private Instant lastSubmittedAt = null;

    public synchronized Result<String, MarketIntelError> handle(GenerateMarketReportCommand command) {
        Result<Void, MarketIntelError> rateLimit = enforceRateLimit();
        if (rateLimit instanceof Result.Failure<Void, MarketIntelError> failure) {
            return Result.failure(failure.error());
        }

        ReportJob job = ReportJob.pending(command.jobId());
        jobTracker.track(job);
        lastSubmittedAt = Instant.now();

        log.info("Dispatching async report generation for jobId: {}", command.jobId());
        processor.process(command.jobId());

        return Result.success(command.jobId());
    }

    private Result<Void, MarketIntelError> enforceRateLimit() {
        boolean anyActive = jobTracker.getAll().stream()
                .anyMatch(j -> j.status().isActive());

        if (anyActive) {
            return Result.failure(new MarketIntelError.GenerationInProgress());
        }

        int cooldownMinutes = reportProperties.minGenerationIntervalMinutes();
        if (lastSubmittedAt != null && cooldownMinutes > 0) {
            Duration elapsed = Duration.between(lastSubmittedAt, Instant.now());
            Duration cooldown = Duration.ofMinutes(cooldownMinutes);
            if (elapsed.compareTo(cooldown) < 0) {
                long remainSec = cooldown.minus(elapsed).getSeconds() + 1;
                return Result.failure(new MarketIntelError.RateLimitExceeded(remainSec));
            }
        }

        return Result.success(null);
    }
}
