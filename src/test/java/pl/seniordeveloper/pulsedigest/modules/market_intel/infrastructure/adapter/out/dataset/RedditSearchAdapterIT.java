package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RedditProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RedditSearchAdapterIT {

    private WireMockServer wireMock;
    private RedditSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RedditProperties reddit =
                new RedditProperties(List.of("java"), 5, 10);
        adapter = new RedditSearchAdapter(new ObjectMapper(), reddit);
        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
        Field restClientField = RedditSearchAdapter.class.getDeclaredField("restClient");
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
    void fetchTopPostsParsesExternalAndPermalinkUrlsAndFiltersLowScore() {
        wireMock.stubFor(get(urlPathEqualTo("/r/java/top.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": {
                                    "children": [
                                      {
                                        "data": {
                                          "title": "External post",
                                          "url": "https://example.com/article",
                                          "permalink": "/r/java/comments/1/external",
                                          "score": 15
                                        }
                                      },
                                      {
                                        "data": {
                                          "title": "Self post",
                                          "url": "https://www.reddit.com/r/java/comments/2/self",
                                          "permalink": "/r/java/comments/2/self",
                                          "score": 20
                                        }
                                      },
                                      {
                                        "data": {
                                          "title": "Low score",
                                          "url": "https://example.com/low",
                                          "permalink": "/r/java/comments/3/low",
                                          "score": 5
                                        }
                                      }
                                    ]
                                  }
                                }
                                """)));

        List<RedditPost> posts = adapter.fetchTopPosts();

        assertThat(posts).hasSize(2);
        assertThat(posts.getFirst().url()).isEqualTo("https://example.com/article");
        assertThat(posts.get(1).url()).isEqualTo("https://www.reddit.com/r/java/comments/2/self");
    }

    @Test
    void fetchTopPostsReturnsEmptyWhenPayloadHasNoChildren() {
        wireMock.stubFor(get(urlPathEqualTo("/r/java/top.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":null}")));

        assertThat(adapter.fetchTopPosts()).isEmpty();
    }
}
