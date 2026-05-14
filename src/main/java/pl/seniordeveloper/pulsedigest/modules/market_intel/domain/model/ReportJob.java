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
        ReportData report,   // null dopóki raport nie został wygenerowany
        String error,        // null chyba że status jest błędowy
        Instant createdAt,
        Instant completedAt  // null dopóki status nie jest terminalny
) {

    /**
     * Fabryka: nowe zadanie w stanie PENDING.
     */
    public static ReportJob pending(String jobId) {
        return new ReportJob(jobId, ReportJobStatus.PENDING, null, null, Instant.now(), null);
    }

    /**
     * Fabryka: zadanie odtworzone jako DELIVERED (np. z dysku).
     */
    public static ReportJob done(String jobId, ReportData report, Instant completedAt) {
        return new ReportJob(jobId, ReportJobStatus.DELIVERED, report, null, completedAt, completedAt);
    }

    /**
     * Kopia z nowym statusem IN_PROGRESS.
     */
    public ReportJob inProgress() {
        return new ReportJob(jobId, ReportJobStatus.IN_PROGRESS, null, null, createdAt, null);
    }

    /**
     * Kopia z wygenerowanym raportem.
     */
    public ReportJob generated(ReportData generatedReport) {
        return new ReportJob(jobId, ReportJobStatus.GENERATED, generatedReport, null, createdAt, null);
    }

    /**
     * Kopia po zapisie raportu w trwałym storage.
     */
    public ReportJob persisted() {
        return new ReportJob(jobId, ReportJobStatus.PERSISTED, report, null, createdAt, null);
    }

    /**
     * Kopia w trakcie dostarczania maila.
     */
    public ReportJob delivering() {
        return new ReportJob(jobId, ReportJobStatus.DELIVERING, report, null, createdAt, null);
    }

    /**
     * Kopia z wynikiem i statusem DELIVERED.
     */
    public ReportJob delivered() {
        return new ReportJob(jobId, ReportJobStatus.DELIVERED, report, null, createdAt, Instant.now());
    }

    /**
     * Kopia z raportem zapisanym, ale nieudanym dostarczeniem maila.
     */
    public ReportJob emailFailed(String errorMessage) {
        return new ReportJob(jobId, ReportJobStatus.EMAIL_FAILED, report, errorMessage, createdAt, Instant.now());
    }

    /**
     * Kopia z komunikatem błędu i statusem ERROR.
     */
    public ReportJob error(String errorMessage) {
        return new ReportJob(jobId, ReportJobStatus.ERROR, null, errorMessage, createdAt, Instant.now());
    }
}
