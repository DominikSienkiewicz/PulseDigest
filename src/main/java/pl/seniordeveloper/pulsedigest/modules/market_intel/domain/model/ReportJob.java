package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;

/**
 * Snapshot stanu asynchronicznego zadania generowania raportu.
 * Immutable record – nowy obiekt tworzony przy każdej zmianie stanu.
 * <p>
 * Pole report zmienione z String na ReportData – ustrukturyzowany JSON
 * eliminuje problemy z ucinaniem tekstu.
 */
public record ReportJob(
        String jobId,
        ReportJobStatus status,
        ReportData report,   // null dopóki status != DONE
        String error,        // null chyba że status == ERROR
        Instant createdAt,
        Instant completedAt  // null dopóki status != DONE/ERROR
) {

    /**
     * Fabryka: nowe zadanie w stanie PENDING.
     */
    public static ReportJob pending(String jobId) {
        return new ReportJob(jobId, ReportJobStatus.PENDING, null, null, Instant.now(), null);
    }

    /**
     * Fabryka: zadanie odtworzone jako DONE (np. z dysku).
     */
    public static ReportJob done(String jobId, ReportData report, Instant completedAt) {
        return new ReportJob(jobId, ReportJobStatus.DONE, report, null, completedAt, completedAt);
    }

    /**
     * Kopia z nowym statusem IN_PROGRESS.
     */
    public ReportJob inProgress() {
        return new ReportJob(jobId, ReportJobStatus.IN_PROGRESS, null, null, createdAt, null);
    }

    /**
     * Kopia z wynikiem i statusem DONE.
     */
    public ReportJob done(ReportData report) {
        return new ReportJob(jobId, ReportJobStatus.DONE, report, null, createdAt, Instant.now());
    }

    /**
     * Kopia z komunikatem błędu i statusem ERROR.
     */
    public ReportJob error(String errorMessage) {
        return new ReportJob(jobId, ReportJobStatus.ERROR, null, errorMessage, createdAt, Instant.now());
    }
}
