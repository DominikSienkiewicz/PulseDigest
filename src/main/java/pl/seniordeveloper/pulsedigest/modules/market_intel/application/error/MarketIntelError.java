package pl.seniordeveloper.pulsedigest.modules.market_intel.application.error;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.shared.error.DomainError;

/**
 * The module's failure vocabulary.
 *
 * <p>Both variants describe the one thing that can go wrong in a single-shot batch: the run
 * finished, and it did not deliver. They replace {@code JobNotFound} and {@code ReportNotAvailable},
 * which existed only for two query services that nothing ever called and were removed on
 * 2026-10-18 — leaving this interface briefly empty, which Java rejects outright for a sealed type.
 * That was the useful signal: the module's only use case was returning a {@code Result} whose
 * failure side could not be constructed, so the type documented a possibility that did not exist.
 */
public sealed interface MarketIntelError extends DomainError
        permits MarketIntelError.PipelineFailed, MarketIntelError.JobVanished {

    default String code() {
        return "MARKET_INTEL_ERROR";
    }

    /**
     * The pipeline ran to a terminal state other than {@code DELIVERED} — a source-wide blackout, a
     * failed synthesis, or an email that could not be sent. {@code detail} carries the job's own
     * error text when it has one.
     */
    record PipelineFailed(String jobId, ReportJobStatus status, String detail) implements MarketIntelError {

        public String message() {
            String base = "Digest run " + jobId + " ended in " + status;
            return detail == null || detail.isBlank() ? base : base + ": " + detail;
        }
    }

    /**
     * The tracker holds no job for an id it was just given. Not reachable through normal flow; kept
     * because the alternative is a silent {@code exit 0} on a run that produced nothing.
     */
    record JobVanished(String jobId) implements MarketIntelError {

        public String message() {
            return "Digest run " + jobId + " left no job in the tracker";
        }
    }
}
