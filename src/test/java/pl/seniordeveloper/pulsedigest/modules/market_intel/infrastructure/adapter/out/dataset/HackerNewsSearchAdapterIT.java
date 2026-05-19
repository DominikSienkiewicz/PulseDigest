package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HackerNewsProperties;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HackerNewsSearchAdapterIT {

    private static final String SINGLE_HIT_RESPONSE = """
            {"hits":[{"title":"Java 26 released","url":"https://example.com/java",
                       "points":50,"objectID":"abc"}]}
            """;

    private WireMockServer wireMock;
    private HackerNewsSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        String baseUrl = "http://localhost:" + wireMock.port();
        HackerNewsProperties props =
                new HackerNewsProperties(baseUrl, List.of("java"), 15, 25);
        adapter = new HackerNewsSearchAdapter(new ObjectMapper(), props);
        adapter.init();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchesAndParsesHackerNewsPosts() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SINGLE_HIT_RESPONSE)));

        List<HackerNewsPost> posts = adapter.fetchTopDiscussions();

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).title()).isEqualTo("Java 26 released");
        assertThat(posts.get(0).points()).isEqualTo(50);
    }

    @Test
    void propagatesHttpErrorOnApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.fetchTopDiscussions())
                .isInstanceOf(RuntimeException.class);
    }
}
