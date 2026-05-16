package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RateLimitPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GenerateMarketReportServiceTest {

    private final MarketIntelJobTracker jobTracker = new MarketIntelJobTracker();
    private final GenerateMarketReportProcessor processor = mock(GenerateMarketReportProcessor.class);
    private final RateLimitPolicy rateLimitPolicy = new RateLimitPolicy(Duration.ofMinutes(30));

    @Test
    void rejectsNewJobWhenAnotherJobIsActive() {
        jobTracker.track(ReportJob.pending("already-running"));
        GenerateMarketReportService service =
                new GenerateMarketReportService(jobTracker, processor, rateLimitPolicy);

        Result<String, MarketIntelError> result = service.handle(new GenerateMarketReportCommand("new-job"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<String, MarketIntelError>) result).error())
                .isInstanceOf(MarketIntelError.GenerationInProgress.class);
    }
}
