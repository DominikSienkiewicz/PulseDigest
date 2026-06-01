package pl.seniordeveloper.pulsedigest.shared.infrastructure.http;

import org.springframework.web.client.RestClientResponseException;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaSignals;

/**
 * Helper for outbound adapters: turns a swallowed HTTP failure into a propagating
 * {@link QuotaExhaustedException} when (and only when) the failure is a quota / rate-limit
 * exhaustion. Non-quota failures are left untouched so adapters keep degrading gracefully.
 *
 * <p>Usage — as the first statement of a catch block that would otherwise return an empty list:
 * <pre>{@code
 * } catch (Exception e) {
 *     QuotaErrors.rethrowIfQuota(e);
 *     log.warn("...");
 *     return List.of();
 * }
 * }</pre>
 */
public final class QuotaErrors {

    private QuotaErrors() {
    }

    /** Rethrows as {@link QuotaExhaustedException} if {@code e} indicates quota exhaustion; otherwise no-op. */
    public static void rethrowIfQuota(Throwable e) {
        if (indicatesQuota(e)) {
            throw new QuotaExhaustedException("QUOTA_EXHAUSTED: " + rootMessage(e), e);
        }
    }

    /** Inspects the whole cause chain for an HTTP 402/429 (or quota-flavored 403) or quota wording. */
    public static boolean indicatesQuota(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof QuotaExhaustedException) {
                return true;
            }
            if (t instanceof RestClientResponseException http) {
                int code = http.getStatusCode().value();
                if (code == 429 || code == 402) {
                    return true;
                }
                if (code == 403 && QuotaSignals.matches(http.getMessage())) {
                    return true;
                }
            }
            if (QuotaSignals.matches(t.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
