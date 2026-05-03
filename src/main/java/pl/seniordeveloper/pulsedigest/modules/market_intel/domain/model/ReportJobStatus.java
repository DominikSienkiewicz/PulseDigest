package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Status asynchronicznego zadania generowania raportu.
 */
public enum ReportJobStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    ERROR
}
