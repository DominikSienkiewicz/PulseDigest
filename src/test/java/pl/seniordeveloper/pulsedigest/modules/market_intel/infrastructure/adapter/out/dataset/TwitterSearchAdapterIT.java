package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class TwitterSearchAdapterIT {

    private static final String SEARCH_RESPONSE = """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Java agents are moving fast",
                  "author_id": "42",
                  "created_at": "2026-05-14T10:00:00Z",
                  "public_metrics": {
                    "retweet_count": 3,
                    "like_count": 10,
                    "reply_count": 2,
                    "quote_count": 1
                  }
                },
                {
                  "id": "2",
                  "text": "Unknown author still maps",
                  "author_id": "99",
                  "created_at": "2026-05-14T10:01:00Z"
                }
              ],
              "includes": {
                "users": [
                  {"id": "42", "name": "Alice", "username": "alice"}
                ]
              }
            }
            """;

    private WireMockServer wireMock;
    private TwitterSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        TwitterProperties properties = new TwitterProperties(
                "token",
                List.of("java"),
                List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"));
        adapter = new TwitterSearchAdapter(new ObjectMapper(), properties);
        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();

        Field restClientField = TwitterSearchAdapter.class.getDeclaredField("restClient");
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
    void searchInfluencerTweetsBatchesAccountsAndMapsUsers() {
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SEARCH_RESPONSE)));

        List<Tweet> tweets = adapter.searchInfluencerTweets();

        assertThat(tweets).hasSize(4);
        assertThat(tweets.getFirst().authorUsername()).isEqualTo("alice");
        assertThat(tweets.getFirst().likeCount()).isEqualTo(10);
        assertThat(tweets.get(1).authorUsername()).isEqualTo("99");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/tweets/search/recent")))).hasSize(2);
    }

    @Test
    void searchTopicTweetsReturnsEmptyForErrorPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[{\"message\":\"rate limit\"}]}")));

        List<Tweet> tweets = adapter.searchTopicTweets();

        assertThat(tweets).isEmpty();
    }
}
