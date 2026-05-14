package pl.seniordeveloper.pulsedigest.modules.market_intel.application.query;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import static org.assertj.core.api.Assertions.assertThat;

class GetMarketIntelStatusServiceTest {

    @Test
    void returnsStatusViewForTrackedJob() {
        MarketIntelJobTracker tracker = new MarketIntelJobTracker();
        tracker.track(ReportJob.pending("job-1").inProgress());
        GetMarketIntelStatusService service = new GetMarketIntelStatusService(tracker);

        Result<MarketIntelStatusView, MarketIntelError> result =
                service.handle(new GetMarketIntelStatusQuery("job-1"));

        assertThat(result.getValue().jobId()).isEqualTo("job-1");
        assertThat(result.getValue().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void returnsJobNotFoundForMissingJob() {
        GetMarketIntelStatusService service = new GetMarketIntelStatusService(new MarketIntelJobTracker());

        Result<MarketIntelStatusView, MarketIntelError> result =
                service.handle(new GetMarketIntelStatusQuery("missing"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.getError()).isInstanceOf(MarketIntelError.JobNotFound.class);
    }
}
