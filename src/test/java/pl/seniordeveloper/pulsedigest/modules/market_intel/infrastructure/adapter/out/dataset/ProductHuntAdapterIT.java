package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ProductHuntAdapterIT {

    private WireMockServer wireMock;
    private ProductHuntAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ReportProperties.ProductHuntProperties props =
                new ReportProperties.ProductHuntProperties(
                        "http://localhost:" + wireMock.port() + "/",
                        "test-token",
                        100,
                        72,
                        List.of("Artificial Intelligence"));
        adapter = new ProductHuntAdapter(props, new ObjectMapper());

        // Force HTTP/1.1 — WireMock 2.x is unreliable with HTTP/2 POST+body (RST_STREAM)
        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/")
                .requestFactory(new SimpleClientHttpRequestFactory())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = ProductHuntAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesLaunchesViaGraphQL() {
        String recent = LocalDateTime.now().minusHours(4).format(DateTimeFormatter.ISO_DATE_TIME);
        String response = """
                {"data":{"posts":{"edges":[
                  {"node":{
                    "name":"DevAssistant",
                    "tagline":"AI helper for engineers",
                    "url":"https://www.producthunt.com/posts/devassistant",
                    "votesCount":350,
                    "createdAt":"%s",
                    "topics":{"edges":[{"node":{"name":"Artificial Intelligence"}}]}
                  }}
                ]}}}
                """.formatted(recent);

        wireMock.stubFor(post(urlPathEqualTo("/"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response)));

        List<ProductHuntPost> posts = adapter.fetchProductLaunches();

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).name()).isEqualTo("DevAssistant");
        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(post(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<ProductHuntPost> posts = adapter.fetchProductLaunches();

        assertThat(posts).isEmpty();
    }
}
