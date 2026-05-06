package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityAdvisoryAdapterIT {

    private WireMockServer wireMock;
    private SecurityAdvisoryAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ReportProperties.SecurityAdvisoriesProperties props =
                new ReportProperties.SecurityAdvisoriesProperties(
                        "http://localhost:" + wireMock.port(),
                        50,
                        72,
                        List.of("HIGH", "CRITICAL"),
                        List.of("maven", "npm"));
        adapter = new SecurityAdvisoryAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        Field restClientField = SecurityAdvisoryAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, testClient);
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchesAndParsesCriticalAdvisory() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
        String response = """
                [{
                  "ghsa_id": "GHSA-1234-5678-90ab",
                  "summary": "Critical RCE in Spring Foo",
                  "severity": "critical",
                  "published_at": "%s",
                  "html_url": "https://github.com/advisories/GHSA-1234-5678-90ab",
                  "vulnerabilities": [{"package": {"ecosystem": "maven", "name": "org.springframework:foo"}}]
                }]
                """.formatted(recent);

        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        List<SecurityAdvisory> advisories = adapter.fetchSecurityAdvisories();

        assertThat(advisories).hasSize(1);
        assertThat(advisories.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<SecurityAdvisory> advisories = adapter.fetchSecurityAdvisories();

        assertThat(advisories).isEmpty();
    }
}
