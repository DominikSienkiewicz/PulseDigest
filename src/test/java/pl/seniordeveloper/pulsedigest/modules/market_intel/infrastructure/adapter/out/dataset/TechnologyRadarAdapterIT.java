package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechnologyRadarProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TechnologyRadarAdapterIT {

    private static final String RADAR_RESPONSE = """
            [{"name":"WebAssembly","ring":"adopt","quadrant":"platforms",
              "description":"Browser-native code.","url":"https://example.com/wasm"}]
            """;

    private WireMockServer wireMock;
    private TechnologyRadarAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        TechnologyRadarProperties props =
                new TechnologyRadarProperties(
                        "http://localhost:" + wireMock.port(),
                        "/",
                        6);
        adapter = new TechnologyRadarAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = TechnologyRadarAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesRadarEntries() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RADAR_RESPONSE)));

        List<RadarEntry> entries = adapter.fetchTechRadarEntries();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).name()).isEqualTo("WebAssembly");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void propagatesHttpErrorOnApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.fetchTechRadarEntries())
                .isInstanceOf(RuntimeException.class);
    }
}
