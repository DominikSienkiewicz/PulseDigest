package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Status zadania generowania raportu.
 *
 * <p>Constants only. Until 2026-10-18 this enum also carried {@code isActive()},
 * {@code isTerminal()} and {@code hasReportAvailable()} — predicates from the HTTP era, when
 * something outside asked about a job's state. The first two lost their callers when the
 * job-lifecycle apparatus was removed (K2), the third along with the query services. What needs a
 * decision today compares against {@code DELIVERED} directly, because that is the only value that
 * changes anything: the process exit code depends on it and on nothing else here.
 */
public enum ReportJobStatus {
    PENDING,
    IN_PROGRESS,
    GENERATED,
    PERSISTED,
    DELIVERING,
    DELIVERED,
    EMAIL_FAILED,
    ERROR
}
