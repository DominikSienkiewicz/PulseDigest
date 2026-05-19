package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CncfLandscapeProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CncfLandscapeAdapterIT {

    private static final String COMMITS_RESPONSE = """
            [
              {"commit": {"message": "Promote OpenTelemetry to graduated",
                          "committer": {"date": "2099-05-06T10:00:00.000Z"}}}
            ]
            """;

    private WireMockServer wireMock;
    private CncfLandscapeAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        CncfLandscapeProperties props =
                new CncfLandscapeProperties(
                        "http://localhost:" + wireMock.port(),
                        7,
                        List.of("graduated"));
        adapter = new CncfLandscapeAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        Field restClientField = CncfLandscapeAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesLandscapeChanges() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(COMMITS_RESPONSE)));

        List<CncfProjectUpdate> changes = adapter.fetchCncfLandscapeChanges();

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).status()).isEqualTo("Graduated");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void propagatesHttpErrorOnApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.fetchCncfLandscapeChanges())
                .isInstanceOf(RuntimeException.class);
    }
}
