package pl.seniordeveloper.pulsedigest.shared.infrastructure.http;

import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaSignals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared RestClient factory for outbound HTTP adapters.
 */
@Slf4j
public final class ExternalRestClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MILLIS = 250L;
    private static final long JITTER_MILLIS = 250L;
    private static final long MAX_BACKOFF_MILLIS = 20_000L;

    private ExternalRestClients() {
    }

    public static RestClient.Builder builder() {
        return builder(READ_TIMEOUT);
    }

    public static RestClient.Builder builder(Duration readTimeout) {
        return RestClient.builder()
                .requestFactory(requestFactory(readTimeout))
                .requestInterceptor(ExternalRestClients::retry);
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private static ClientHttpResponse retry(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String host = hostOf(request.getURI());
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long retryAfterMillis = -1;
            try {
                ClientHttpResponse response = classifyThrottle(execution.execute(request, body));
                HttpStatusCode statusCode = response.getStatusCode();
                if (isDepletedBudget(response)) {
                    recordRetry(host, "budget_depleted");
                    log.debug("Not retrying {} {} — the account's budget is depleted",
                            request.getMethod(), request.getURI());
                    return response;
                }
                if (!isRetryable(statusCode) || attempt == MAX_ATTEMPTS) {
                    if (isRetryable(statusCode) && attempt == MAX_ATTEMPTS) {
                        recordRetry(host, "exhausted_" + statusCode.value());
                    }
                    return response;
                }
                retryAfterMillis = parseRetryAfterMillis(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                response.close();
                recordRetry(host, "http_" + statusCode.value());
                log.debug("Retrying {} {} after HTTP {} (attempt {}/{})",
                        request.getMethod(), request.getURI(), statusCode, attempt, MAX_ATTEMPTS);
            } catch (IOException e) {
                lastException = e;
                recordIoFailure(request, host, e, attempt);
            }
            sleepBeforeNextAttempt(attempt, retryAfterMillis);
        }
        throw lastException != null ? lastException : new IOException("HTTP request failed without response");
    }

    // Rethrows the original failure once the attempts are spent, so the caller never sees a wrapper.
    private static void recordIoFailure(HttpRequest request, String host, IOException failure, int attempt)
            throws IOException {
        if (attempt == MAX_ATTEMPTS) {
            recordRetry(host, "exhausted_io");
            throw failure;
        }
        recordRetry(host, "io_error");
        log.debug("Retrying {} {} after I/O error: {} (attempt {}/{})",
                request.getMethod(), request.getURI(), failure.getMessage(), attempt, MAX_ATTEMPTS);
    }

    /**
     * Parses an HTTP {@code Retry-After} header value into milliseconds. Supports both forms from
     * RFC 9110: delta-seconds (an integer) and an HTTP-date. Returns {@code -1} when the header is
     * absent or unparseable, so the caller falls back to the default backoff.
     */
    static long parseRetryAfterMillis(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return -1;
        }
        String value = headerValue.strip();
        try {
            return Long.parseLong(value) * 1000L;
        } catch (NumberFormatException _) {
            // not delta-seconds — try HTTP-date below
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0L, Duration.between(Instant.now(), when.toInstant()).toMillis());
        } catch (DateTimeParseException _) {
            return -1;
        }
    }

    private static void recordRetry(String host, String reason) {
        Metrics.counter("http.client.retries", "host", host, "reason", reason).increment();
    }

    private static String hostOf(URI uri) {
        String host = uri.getHost();
        return host != null ? host : "unknown";
    }

    private static boolean isRetryable(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }

    /**
     * Buffers a throttled response so its body can be inspected without being consumed. Only 429s
     * are buffered: they are rare, their bodies are tiny, and every other response would pay the
     * memory cost for nothing.
     */
    private static ClientHttpResponse classifyThrottle(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().value() != 429) {
            return response;
        }
        try (response) {
            return new BufferedResponse(response.getStatusCode(), response.getStatusText(),
                    response.getHeaders(), response.getBody().readAllBytes());
        }
    }

    // A 429 saying "insufficient_quota" is a billing state, not a speed limit: no backoff can fix it.
    private static boolean isDepletedBudget(ClientHttpResponse response) throws IOException {
        return response instanceof BufferedResponse buffered
                && QuotaSignals.indicatesDepletedBudget(buffered.bodyAsText());
    }

    /** A fully-read response whose body can be handed to the caller after we have inspected it. */
    private record BufferedResponse(HttpStatusCode statusCode, String statusText,
                                    HttpHeaders headers, byte[] body) implements ClientHttpResponse {

        String bodyAsText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return statusCode;
        }

        @Override
        public String getStatusText() {
            return statusText;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            // nothing to release: the underlying response was closed when this one was created
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BufferedResponse that
                    && Objects.equals(statusCode, that.statusCode)
                    && Objects.equals(statusText, that.statusText)
                    && Objects.equals(headers, that.headers)
                    && Arrays.equals(body, that.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statusCode, statusText, headers, Arrays.hashCode(body));
        }

        @Override
        public String toString() {
            return "BufferedResponse[statusCode=" + statusCode + ", statusText=" + statusText
                    + ", headers=" + headers + ", body=" + Arrays.toString(body) + ']';
        }
    }

    // Honors a server-provided Retry-After header (capped) when present, otherwise linear backoff.
    // A small random jitter is always added so parallel source fetches do not retry in lock-step.
    private static void sleepBeforeNextAttempt(int attempt, long retryAfterMillis) throws IOException {
        long base = retryAfterMillis >= 0
                ? Math.min(retryAfterMillis, MAX_BACKOFF_MILLIS)
                : BACKOFF_MILLIS * attempt;
        long jitter = ThreadLocalRandom.current().nextLong(JITTER_MILLIS + 1);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during HTTP retry backoff", e);
        }
    }
}
