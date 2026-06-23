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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
                ClientHttpResponse response = execution.execute(request, body);
                HttpStatusCode statusCode = response.getStatusCode();
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
                if (attempt == MAX_ATTEMPTS) {
                    recordRetry(host, "exhausted_io");
                    throw e;
                }
                recordRetry(host, "io_error");
                log.debug("Retrying {} {} after I/O error: {} (attempt {}/{})",
                        request.getMethod(), request.getURI(), e.getMessage(), attempt, MAX_ATTEMPTS);
            }
            sleepBeforeNextAttempt(attempt, retryAfterMillis);
        }
        throw lastException != null ? lastException : new IOException("HTTP request failed without response");
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
