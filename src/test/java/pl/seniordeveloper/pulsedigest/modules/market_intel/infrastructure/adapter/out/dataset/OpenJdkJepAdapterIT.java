package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OpenJdkJepAdapterIT {

    private static final String COMMITS_RESPONSE = """
            [
              {"commit": {"message": "JEP-456: Virtual Threads\\n\\nIntegrated.",
                          "committer": {"date": "2099-05-06T10:00:00.000Z"}}}
            ]
            """;

    private WireMockServer wireMock;
    private OpenJdkJepAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ReportProperties.OpenJdkProperties props =
                new ReportProperties.OpenJdkProperties(
                        "http://localhost:" + wireMock.port(),
                        7,
                        List.of("Integrated"));
        adapter = new OpenJdkJepAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        Field restClientField = OpenJdkJepAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesJepUpdates() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(COMMITS_RESPONSE)));

        List<JepUpdate> updates = adapter.fetchJepUpdates();

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).jepId()).isEqualTo("JEP-456");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<JepUpdate> updates = adapter.fetchJepUpdates();

        assertThat(updates).isEmpty();
    }
}
