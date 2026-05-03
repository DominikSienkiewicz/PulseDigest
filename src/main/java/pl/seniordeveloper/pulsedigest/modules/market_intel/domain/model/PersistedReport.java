package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;

public record PersistedReport(
        ReportData report,
        String jobId,
        Instant generatedAt,
        int tweetCount,
        int hnCount,
        int githubCount
) {
}
