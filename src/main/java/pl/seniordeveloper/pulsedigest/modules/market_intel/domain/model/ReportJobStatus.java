package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Status asynchronicznego zadania generowania raportu.
 */
public enum ReportJobStatus {
    PENDING,
    IN_PROGRESS,
    GENERATED,
    PERSISTED,
    DELIVERING,
    DELIVERED,
    EMAIL_FAILED,
    ERROR;

    public boolean isActive() {
        return this == PENDING
                || this == IN_PROGRESS
                || this == GENERATED
                || this == PERSISTED
                || this == DELIVERING;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == EMAIL_FAILED || this == ERROR;
    }

    public boolean hasReportAvailable() {
        return this == GENERATED
                || this == PERSISTED
                || this == DELIVERING
                || this == DELIVERED
                || this == EMAIL_FAILED;
    }
}
