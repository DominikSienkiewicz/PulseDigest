package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final int DAYS_BACK = 2;

    private WireMockServer wireMock;
    private TwitterSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        TwitterProperties properties = new TwitterProperties(
                "token",
                List.of("java"),
                List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"),
                10,
                0);
        ResearchProperties research = new ResearchProperties(3, DAYS_BACK, List.of("a1"));
        adapter = new TwitterSearchAdapter(new ObjectMapper(), properties, research);
        injectTestClient(adapter);
    }

    private void injectTestClient(TwitterSearchAdapter target) throws Exception {
        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
        Field restClientField = TwitterSearchAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(target, testClient);
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
    void searchAnthropicTweetsUsesConfiguredWindowMaxResultsAndRecencySort() {
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SEARCH_RESPONSE)));

        ZonedDateTime before = ZonedDateTime.now(ZoneOffset.UTC);
        adapter.searchAnthropicTweets();
        ZonedDateTime after = ZonedDateTime.now(ZoneOffset.UTC);

        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/tweets/search/recent"))
                .withQueryParam("max_results", equalTo("30"))
                .withQueryParam("sort_order", equalTo("recency")));
        assertThat(requests).hasSize(1);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String startTimeRaw = requests.getFirst().queryParameter("start_time").firstValue();
        ZonedDateTime startTime = ZonedDateTime.parse(startTimeRaw, fmt.withZone(ZoneOffset.UTC));
        ZonedDateTime expectedLow = before.minusDays(DAYS_BACK).minus(Duration.ofSeconds(5));
        ZonedDateTime expectedHigh = after.minusDays(DAYS_BACK).plus(Duration.ofSeconds(5));
        assertThat(startTime).isBetween(expectedLow, expectedHigh);
    }

    @Test
    void searchAnthropicTweetsPropagatesHttpErrorSoSourceIsMarkedFailed() {
        // Regression: X API HTTP 402 CreditsDepleted (and other 4xx/5xx) used to be swallowed,
        // making the source look like "0 results" instead of "failed". Now the error must propagate
        // so MarketResearchService.fetchSource can surface it in SourceFetchReport.
        String creditsDepletedBody = "{\"title\":\"CreditsDepleted\","
                + "\"detail\":\"Your enrolled account does not have any credits.\","
                + "\"type\":\"https://api.twitter.com/2/problems/credits\"}";
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody(creditsDepletedBody)));

        assertThatThrownBy(() -> adapter.searchAnthropicTweets())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("402")
                .hasMessageContaining("CreditsDepleted");
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

    @Test
    void searchInfluencerTweetsStopsWhenCallBudgetExhausted() throws Exception {
        // 17 accounts → ceil(17/8) = 3 batch calls, but the per-run budget caps execution at 2.
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SEARCH_RESPONSE)));

        List<String> manyAccounts = new ArrayList<>();
        for (int i = 1; i <= 17; i++) {
            manyAccounts.add("acc" + i);
        }
        TwitterProperties capped = new TwitterProperties("token", List.of("java"), manyAccounts, 2, 0);
        TwitterSearchAdapter cappedAdapter = new TwitterSearchAdapter(
                new ObjectMapper(), capped, new ResearchProperties(3, DAYS_BACK, List.of("acc1")));
        injectTestClient(cappedAdapter);

        cappedAdapter.searchInfluencerTweets();

        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/tweets/search/recent"))))
                .as("the 3rd batch call must be skipped once the 2-call budget is spent")
                .hasSize(2);
    }

    @Test
    void minFavesOperatorAppendedToQueryWhenConfigured() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/tweets/search/recent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SEARCH_RESPONSE)));

        TwitterProperties withFaves = new TwitterProperties(
                "token", List.of("java OR spring"), List.of("a1"), 10, 3);
        TwitterSearchAdapter favesAdapter = new TwitterSearchAdapter(
                new ObjectMapper(), withFaves, new ResearchProperties(3, DAYS_BACK, List.of("a1")));
        injectTestClient(favesAdapter);

        favesAdapter.searchTopicTweets();

        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/tweets/search/recent")));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().queryParameter("query").firstValue())
                .as("min_faves:N must be appended server-side when twitter.min-faves > 0")
                .contains("min_faves:3");
    }
}
