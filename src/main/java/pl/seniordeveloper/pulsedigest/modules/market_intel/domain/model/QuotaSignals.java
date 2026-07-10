package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * Framework-free detection of API quota / rate-limit exhaustion from raw error text.
 *
 * <p>Matches the human-readable reason phrases that bubble up from outbound HTTP failures
 * (e.g. {@code 429 Too Many Requests}, {@code 402 Payment Required}) and provider-specific
 * quota wording (OpenAI {@code insufficient_quota}, GitHub {@code rate limit exceeded},
 * X API {@code CreditsDepleted}, YouTube {@code quotaExceeded}). Used to decide whether a
 * failed source warrants a "top up this account" banner rather than a generic warning.
 */
public final class QuotaSignals {

    private static final List<String> MARKERS = List.of(
            "quota_exhausted",
            "insufficient_quota",
            "quotaexceeded",
            "quota exceeded",
            "exceeded your current quota",
            "quota",
            "too many requests",
            "rate limit",
            "ratelimit",
            "rate_limit",
            "payment required",
            "creditsdepleted",
            "credits depleted",
            "out of credits",
            "billing"
    );

    /**
     * The strict subset of {@link #MARKERS} that means "the account is out of budget", as opposed to
     * "you are going too fast right now". Throttling is worth a backoff; a depleted balance is not —
     * retrying it only burns round-trips against an account that cannot recover without a payment.
     */
    private static final List<String> DEPLETED_BUDGET_MARKERS = List.of(
            "quota_exhausted",
            "insufficient_quota",
            "quotaexceeded",
            "quota exceeded",
            "exceeded your current quota",
            "payment required",
            "creditsdepleted",
            "credits depleted",
            "out of credits",
            "billing"
    );

    private QuotaSignals() {
    }

    /**
     * Returns {@code true} when the text says the account's budget is spent, rather than merely
     * rate-limited. Callers use this to decide whether a failure is worth retrying at all.
     */
    public static boolean indicatesDepletedBudget(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return DEPLETED_BUDGET_MARKERS.stream().anyMatch(lower::contains);
    }

    /** Returns {@code true} when the text carries a recognizable quota / rate-limit signature. */
    public static boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(lower::contains);
    }
}
