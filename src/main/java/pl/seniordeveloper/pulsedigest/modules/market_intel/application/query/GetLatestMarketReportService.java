package pl.seniordeveloper.pulsedigest.modules.market_intel.application.query;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

/**
 * Tries in-memory cache first, falls back to persistent storage.
 */
@RequiredArgsConstructor
@Service
public class GetLatestMarketReportService {

    private final MarketIntelJobTracker jobTracker;
    private final ReportStoragePort storagePort;

    public Result<ReportData, MarketIntelError> handle(GetLatestMarketReportQuery query) {
        return jobTracker.getAll().stream()
                .filter(j -> j.status().hasReportAvailable())
                .map(ReportJob::report)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .or(() -> storagePort.getLatest().map(PersistedReport::report))
                .map(Result::<ReportData, MarketIntelError>success)
                .orElse(Result.failure(new MarketIntelError.ReportNotAvailable()));
    }
}
