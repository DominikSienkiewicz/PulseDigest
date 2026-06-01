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

    @Test
    void handlesNullAndBlank() {
        assertThat(QuotaSignals.matches(null)).isFalse();
        assertThat(QuotaSignals.matches("")).isFalse();
        assertThat(QuotaSignals.matches("   ")).isFalse();
    }
}
