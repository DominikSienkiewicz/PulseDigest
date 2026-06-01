package pl.seniordeveloper.pulsedigest.shared.infrastructure.http;

/**
 * Thrown when an outbound API call fails because the account's quota / rate limit is exhausted
 * (depleted credits, HTTP 402/429, provider-specific quota errors). Distinct from generic
 * transient failures so the pipeline can surface a "top up this account" message instead of a
 * silent empty result.
 */
public final class QuotaExhaustedException extends RuntimeException {

    public QuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
