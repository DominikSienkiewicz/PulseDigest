package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HuggingFaceProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HuggingFaceTrendingAdapterIT {

    private static final String MODELS_RESPONSE = """
            [
              {
                "modelId": "meta-llama/Llama-3-8B",
                "pipeline_tag": "text-generation",
                "downloads": 50000,
                "likes": 800,
                "lastModified": "2099-05-01T08:00:00.000Z"
              }
            ]
            """;

    private WireMockServer wireMock;
    private HuggingFaceTrendingAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        HuggingFaceProperties props =
                new HuggingFaceProperties(
                        "http://localhost:" + wireMock.port(),
                        30,
                        10,
                        1000,
                        List.of("text-generation"));
        adapter = new HuggingFaceTrendingAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = HuggingFaceTrendingAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesTrendingModels() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(MODELS_RESPONSE)));

        List<HuggingFaceModel> models = adapter.fetchTrendingModels();

        assertThat(models).hasSize(1);
        assertThat(models.get(0).id()).isEqualTo("meta-llama/Llama-3-8B");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<HuggingFaceModel> models = adapter.fetchTrendingModels();

        assertThat(models).isEmpty();
    }
}
