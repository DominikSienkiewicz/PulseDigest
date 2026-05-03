package pl.seniordeveloper.pulsedigest.modules.market_intel.application.error;

import pl.seniordeveloper.pulsedigest.shared.error.DomainError;

public sealed interface MarketIntelError extends DomainError
        permits MarketIntelError.JobNotFound,
        MarketIntelError.ReportNotAvailable,
        MarketIntelError.GenerationInProgress,
        MarketIntelError.RateLimitExceeded {

    default String code() {
        return "MARKET_INTEL_ERROR";
    }

    record JobNotFound(String jobId) implements MarketIntelError {
        public String message() {
            return "Report job not found: " + jobId;
        }
    }

    record ReportNotAvailable() implements MarketIntelError {
        public String message() {
            return "No completed market intelligence report is available yet";
        }
    }

    record GenerationInProgress() implements MarketIntelError {
        public String message() {
            return "A generation job is already in progress. Please wait for it to finish.";
        }
    }

    record RateLimitExceeded(long retryAfterSeconds) implements MarketIntelError {
        public String message() {
            return "Rate limit: next generation allowed in " + retryAfterSeconds + " seconds.";
        }
    }
}
