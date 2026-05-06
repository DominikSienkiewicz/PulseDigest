package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PackageTrend;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class LibrariesIoAdapterIT {

    private static final String TRENDS_RESPONSE = """
            [
              {"name":"langchain4j","platform":"maven","description":"Java LLM","stars":12000,
               "dependents_count":450,"homepage":"https://github.com/langchain4j/langchain4j",
               "repository_url":"","latest_release_published_at":"2099-05-01T12:00:00.000Z"}
            ]
            """;

    private WireMockServer wireMock;
    private LibrariesIoAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ReportProperties.LibrariesIoProperties props =
                new ReportProperties.LibrariesIoProperties(
                        "http://localhost:" + wireMock.port(),
                        "test-api-key",
                        20,
                        List.of("maven"),
                        90);
        adapter = new LibrariesIoAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = LibrariesIoAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesPackageTrends() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TRENDS_RESPONSE)));

        List<PackageTrend> trends = adapter.fetchPackageTrends();

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).name()).isEqualTo("langchain4j");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<PackageTrend> trends = adapter.fetchPackageTrends();

        assertThat(trends).isEmpty();
    }
}
