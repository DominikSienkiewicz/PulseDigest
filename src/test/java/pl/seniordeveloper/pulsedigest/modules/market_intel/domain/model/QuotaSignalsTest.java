package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaSignalsTest {

    @Test
    void detectsHttp429ReasonPhrase() {
        assertThat(QuotaSignals.matches("429 Too Many Requests on GET request for \"...\""))
                .isTrue();
    }

    @Test
    void detectsHttp402PaymentRequired() {
        assertThat(QuotaSignals.matches("402 Payment Required: \"{\\\"detail\\\":\\\"CreditsDepleted\\\"}\""))
                .isTrue();
    }

    @Test
    void detectsOpenAiInsufficientQuota() {
        assertThat(QuotaSignals.matches("You exceeded your current quota (insufficient_quota)"))
                .isTrue();
    }

    @Test
    void detectsGithubRateLimit() {
        assertThat(QuotaSignals.matches("403 Forbidden: API rate limit exceeded for user"))
                .isTrue();
    }

    @Test
    void detectsYoutubeQuotaExceeded() {
        assertThat(QuotaSignals.matches("403 Forbidden: The request cannot be completed (quotaExceeded)"))
                .isTrue();
    }

    @Test
    void isCaseInsensitive() {
        assertThat(QuotaSignals.matches("RATE LIMIT REACHED")).isTrue();
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(QuotaSignals.matches("Connection reset by peer")).isFalse();
        assertThat(QuotaSignals.matches("500 Internal Server Error")).isFalse();
        assertThat(QuotaSignals.matches("Read timed out")).isFalse();
    }

    // --- indicatesDepletedBudget: the narrow, terminal subset of quota signals ---

    @Test
    void depletedBudgetCoversBillingStatesThatRetryingCannotFix() {
        assertThat(QuotaSignals.indicatesDepletedBudget("{\"error\":{\"code\":\"insufficient_quota\"}}")).isTrue();
        assertThat(QuotaSignals.indicatesDepletedBudget("You exceeded your current quota")).isTrue();
        assertThat(QuotaSignals.indicatesDepletedBudget("402 Payment Required")).isTrue();
        assertThat(QuotaSignals.indicatesDepletedBudget("{\"detail\":\"CreditsDepleted\"}")).isTrue();
    }

    @Test
    void depletedBudgetExcludesPlainThrottlingWhichRetryingDoesFix() {
        // A rate limit says "not now"; a depleted budget says "not until you pay". Only the first
        // is worth a backoff — that is the whole point of separating them.
        assertThat(QuotaSignals.indicatesDepletedBudget("429 Too Many Requests")).isFalse();
        assertThat(QuotaSignals.indicatesDepletedBudget("API rate limit exceeded for user")).isFalse();
        assertThat(QuotaSignals.indicatesDepletedBudget("Retry after 30 seconds")).isFalse();
    }

    @Test
    void depletedBudgetHandlesNullAndBlank() {
        assertThat(QuotaSignals.indicatesDepletedBudget(null)).isFalse();
        assertThat(QuotaSignals.indicatesDepletedBudget("  ")).isFalse();
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(QuotaSignals.matches(null)).isFalse();
        assertThat(QuotaSignals.matches("")).isFalse();
        assertThat(QuotaSignals.matches("   ")).isFalse();
    }
}
