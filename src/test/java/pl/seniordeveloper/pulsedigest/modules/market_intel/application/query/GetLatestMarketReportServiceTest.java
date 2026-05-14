package pl.seniordeveloper.pulsedigest.modules.market_intel.application.query;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GetLatestMarketReportServiceTest {

    @Test
    void returnsReportFromMemoryBeforeStorage() {
        MarketIntelJobTracker tracker = new MarketIntelJobTracker();
        ReportData memoryReport = report("memory");
        ReportData storedReport = report("storage");
        tracker.track(ReportJob.done("job-1", memoryReport, Instant.now()));
        GetLatestMarketReportService service = new GetLatestMarketReportService(
                tracker,
                storageWith(storedReport));

        Result<ReportData, MarketIntelError> result = service.handle(new GetLatestMarketReportQuery());

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.getValue()).isSameAs(memoryReport);
    }

    @Test
    void fallsBackToStorageWhenTrackedJobsHaveNoAvailableReport() {
        MarketIntelJobTracker tracker = new MarketIntelJobTracker();
        ReportData storedReport = report("storage");
        tracker.track(ReportJob.pending("job-1"));
        GetLatestMarketReportService service = new GetLatestMarketReportService(
                tracker,
                storageWith(storedReport));

        Result<ReportData, MarketIntelError> result = service.handle(new GetLatestMarketReportQuery());

        assertThat(result.getValue()).isSameAs(storedReport);
    }

    @Test
    void returnsReportNotAvailableWhenMemoryAndStorageAreEmpty() {
        GetLatestMarketReportService service = new GetLatestMarketReportService(
                new MarketIntelJobTracker(),
                storageWith(null));

        Result<ReportData, MarketIntelError> result = service.handle(new GetLatestMarketReportQuery());

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.getError()).isInstanceOf(MarketIntelError.ReportNotAvailable.class);
    }

    private static ReportStoragePort storageWith(ReportData report) {
        return new ReportStoragePort() {
            @Override
            public void save(PersistedReport persistedReport) {
            }

            @Override
            public Optional<PersistedReport> getLatest() {
                return report == null
                        ? Optional.empty()
                        : Optional.of(new PersistedReport(report, "stored", Instant.now(), 0, 0, 0));
            }
        };
    }

    private static ReportData report(String title) {
        return new ReportData("preview", "editorial", List.of(title), List.of());
    }
}
