package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerateMarketReportServiceTest {

    private final MarketIntelJobTracker jobTracker = new MarketIntelJobTracker();
    private final GenerateMarketReportProcessor processor = mock(GenerateMarketReportProcessor.class);
    private final ReportProperties properties = mock(ReportProperties.class);

    @Test
    void rejectsNewJobWhenAnotherJobIsActive() {
        jobTracker.track(ReportJob.pending("already-running"));
        when(properties.minGenerationIntervalMinutes()).thenReturn(30);
        GenerateMarketReportService service = new GenerateMarketReportService(jobTracker, processor, properties);

        Result<String, MarketIntelError> result = service.handle(new GenerateMarketReportCommand("new-job"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<String, MarketIntelError>) result).error())
                .isInstanceOf(MarketIntelError.GenerationInProgress.class);
    }
}
