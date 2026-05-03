package pl.seniordeveloper.pulsedigest.modules.market_intel.application.query;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

@RequiredArgsConstructor
@Service
public class GetMarketIntelStatusService {

    private final MarketIntelJobTracker jobTracker;

    private static MarketIntelStatusView toView(ReportJob job) {
        return new MarketIntelStatusView(
                job.jobId(),
                job.status().name(),
                job.error(),
                job.createdAt(),
                job.completedAt()
        );
    }

    public Result<MarketIntelStatusView, MarketIntelError> handle(GetMarketIntelStatusQuery query) {
        return jobTracker.getJob(query.jobId())
                .map(GetMarketIntelStatusService::toView)
                .map(Result::<MarketIntelStatusView, MarketIntelError>success)
                .orElse(Result.failure(new MarketIntelError.JobNotFound(query.jobId())));
    }
}
