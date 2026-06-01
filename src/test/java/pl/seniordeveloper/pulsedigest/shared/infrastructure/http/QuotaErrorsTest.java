package pl.seniordeveloper.pulsedigest.shared.infrastructure.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class QuotaErrorsTest {

    @Test
    void http429IsQuota() {
        var e = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(QuotaErrors.indicatesQuota(e)).isTrue();
    }

    @Test
    void http402IsQuota() {
        var e = new HttpClientErrorException(HttpStatus.PAYMENT_REQUIRED);
        assertThat(QuotaErrors.indicatesQuota(e)).isTrue();
    }

    @Test
    void http403WithRateLimitWordingIsQuota() {
        var e = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "API rate limit exceeded", null, null, null);
        assertThat(QuotaErrors.indicatesQuota(e)).isTrue();
    }

    @Test
    void plainHttp403IsNotQuota() {
        var e = new HttpClientErrorException(HttpStatus.FORBIDDEN);
        assertThat(QuotaErrors.indicatesQuota(e)).isFalse();
    }

    @Test
    void quotaWordingNestedInCauseChainIsDetected() {
        var root = new IllegalStateException("insufficient_quota");
        var wrapper = new RuntimeException("synthesis failed", root);
        assertThat(QuotaErrors.indicatesQuota(wrapper)).isTrue();
    }

    @Test
    void unrelatedFailureIsNotQuota() {
        var e = new IOException("Connection reset");
        assertThat(QuotaErrors.indicatesQuota(e)).isFalse();
    }

    @Test
    void rethrowIfQuotaThrowsOnQuota() {
        var e = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        assertThatThrownBy(() -> QuotaErrors.rethrowIfQuota(e))
                .isInstanceOf(QuotaExhaustedException.class)
                .hasMessageContaining("QUOTA_EXHAUSTED");
    }

    @Test
    void rethrowIfQuotaIsNoOpOnNonQuota() {
        var e = new IOException("Read timed out");
        assertThatCode(() -> QuotaErrors.rethrowIfQuota(e)).doesNotThrowAnyException();
    }
}
