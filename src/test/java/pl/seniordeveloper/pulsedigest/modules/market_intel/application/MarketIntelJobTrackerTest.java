package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIntelJobTrackerTest {

    @Test
    void evictExpiredJobsMarksTimedOutActiveJobsAndRemovesOldTerminalJobs() {
        MarketIntelJobTracker tracker = new MarketIntelJobTracker();
        ReportData report = new ReportData("preview", "editorial", List.of(), List.of());
        ReportJob timedOut = new ReportJob(
                "timed-out",
                ReportJobStatus.IN_PROGRESS,
                null,
                null,
                Instant.now().minus(Duration.ofHours(2)),
                null);
        ReportJob expiredDelivered = new ReportJob(
                "expired",
                ReportJobStatus.DELIVERED,
                report,
                null,
                Instant.now().minus(Duration.ofHours(4)),
                Instant.now().minus(Duration.ofHours(3)));
        ReportJob freshDelivered = new ReportJob(
                "fresh",
                ReportJobStatus.DELIVERED,
                report,
                null,
                Instant.now().minus(Duration.ofMinutes(10)),
                Instant.now());

        tracker.track(timedOut);
        tracker.track(expiredDelivered);
        tracker.track(freshDelivered);

        tracker.evictExpiredJobs();

        assertThat(tracker.getJob("timed-out")).get()
                .extracting(ReportJob::status)
                .isEqualTo(ReportJobStatus.ERROR);
        assertThat(tracker.getJob("expired")).isEmpty();
        assertThat(tracker.getJob("fresh")).isPresent();
    }
}
