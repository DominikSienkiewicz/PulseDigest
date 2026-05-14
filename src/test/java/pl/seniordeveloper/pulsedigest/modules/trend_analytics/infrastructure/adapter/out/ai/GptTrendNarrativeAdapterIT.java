package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GptTrendNarrativeAdapterIT {

    private WireMockServer wireMock;
    private GptTrendNarrativeAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new GptTrendNarrativeAdapter(new ObjectMapper());
        Field field = GptTrendNarrativeAdapter.class.getDeclaredField("openAiClient");
        field.setAccessible(true);
        field.set(adapter, RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void narrateBatchPostsJsonModeRequestAndParsesNarratives() throws Exception {
        String content = """
                {
                  "narratives": {
                    "Java": "JVM releases keep coming",
                    "Security": "Security issues repeat"
                  }
                }
                """;
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponse(content))));

        Map<String, String> result = adapter.narrateBatch(List.of(
                new TrendCluster("Java", 3, List.of("JDK 26", "Spring"), null),
                new TrendCluster("Security", 2, List.of("CVE"), null)));

        assertThat(result).containsEntry("Java", "JVM releases keep coming");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"response_format\":{\"type\":\"json_object\"}"))
                .withRequestBody(containing("Java"))
                .withRequestBody(containing("JDK 26")));
    }

    @Test
    void narrateBatchReturnsEmptyForEmptyInputAndForLlmFailure() {
        assertThat(adapter.narrateBatch(List.of())).isEmpty();

        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(adapter.narrateBatch(List.of(new TrendCluster("Java", 3, List.of("JDK"), null))))
                .isEmpty();
    }

    @Test
    void narrateBatchReturnsEmptyWhenPayloadHasNoNarratives() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponse("{}"))));

        assertThat(adapter.narrateBatch(List.of(new TrendCluster("Java", 3, List.of("JDK"), null))))
                .isEmpty();
    }

    private static String openAiResponse(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices",
                List.of(Map.of("message", Map.of("content", content)))));
    }
}
