package pl.seniordeveloper.pulsedigest.shared.infrastructure.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class ExternalRestClientsTest {

    private WireMockServer wireMock;
    private SimpleMeterRegistry registry;
    private RestClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        client = ExternalRestClients.builder().baseUrl(wireMock.baseUrl()).build();
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
        registry.close();
        wireMock.stop();
    }

    @Test
    void incrementsRetryCounterOnHttp5xx() {
        wireMock.stubFor(get(urlEqualTo("/flaky"))
                .willReturn(aResponse().withStatus(500)));

        assertThatRequestCompletes("/flaky");

        double total = retryCounterTotal();
        assertThat(total)
                .as("Three attempts produce two retry events plus one 'exhausted' marker")
                .isEqualTo(3.0);
    }

    @Test
    void incrementsRetryCounterOnHttp429() {
        wireMock.stubFor(get(urlEqualTo("/throttled"))
                .willReturn(aResponse().withStatus(429)));

        assertThatRequestCompletes("/throttled");

        assertThat(retryCounterTotal()).isGreaterThan(0.0);
    }

    @Test
    void doesNotIncrementRetryCounterOnSuccess() {
        wireMock.stubFor(get(urlEqualTo("/ok"))
                .willReturn(aResponse().withStatus(200).withBody("hello")));

        String body = client.get().uri("/ok").retrieve().body(String.class);
        assertThat(body).isEqualTo("hello");
        assertThat(retryCounterTotal()).isZero();
    }

    @Test
    void parsesRetryAfterDeltaSeconds() {
        assertThat(ExternalRestClients.parseRetryAfterMillis("2")).isEqualTo(2000L);
        assertThat(ExternalRestClients.parseRetryAfterMillis("0")).isZero();
    }

    @Test
    void parseRetryAfterReturnsNegativeForMissingOrInvalid() {
        assertThat(ExternalRestClients.parseRetryAfterMillis(null)).isNegative();
        assertThat(ExternalRestClients.parseRetryAfterMillis("   ")).isNegative();
        assertThat(ExternalRestClients.parseRetryAfterMillis("soon")).isNegative();
    }

    @Test
    void parsesRetryAfterHttpDateInTheFuture() {
        String future = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30));

        long millis = ExternalRestClients.parseRetryAfterMillis(future);

        assertThat(millis).isBetween(25_000L, 31_000L);
    }

    @Test
    void honorsRetryAfterHeaderOnThrottledResponse() {
        wireMock.stubFor(get(urlEqualTo("/retry-after"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));

        assertThatRequestCompletes("/retry-after");

        assertThat(retryCounterTotal()).isGreaterThan(0.0);
    }

    private void assertThatRequestCompletes(String path) {
        try {
            client.get().uri(path).retrieve().toBodilessEntity();
        } catch (RuntimeException _) {
            // status-based exceptions from the final response do not block metric assertions
        }
    }

    private double retryCounterTotal() {
        return registry.find("http.client.retries").counters().stream()
                .mapToDouble(c -> c.count())
                .sum();
    }
}
