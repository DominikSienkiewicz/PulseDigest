package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * One edition as it is stored: the report itself plus what it cost to gather.
 *
 * <p>{@code fetchReports} carries the per-source outcome of every source this run touched — the
 * denominator of source yield. Without it the archive records what got published but not what was
 * fetched to publish it, which makes "is Reddit earning its prompt slots?" unanswerable.
 */
public record PersistedReport(
        ReportData report,
        String jobId,
        Instant generatedAt,
        int tweetCount,
        int hnCount,
        int githubCount,
        List<SourceFetchReport> fetchReports
) {

    public PersistedReport {
        fetchReports = fetchReports != null ? List.copyOf(fetchReports) : List.of();
    }

    /** Convenience constructor for editions stored without per-source fetch reports. */
    public PersistedReport(ReportData report, String jobId, Instant generatedAt,
                           int tweetCount, int hnCount, int githubCount) {
        this(report, jobId, generatedAt, tweetCount, hnCount, githubCount, List.of());
    }
}
