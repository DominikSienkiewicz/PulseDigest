package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class BlueskySearchAdapterIT {

    private static final String FEED_RESPONSE = """
            {"feed":[
              {"post":{
                "uri":"at://did:plc:xyz/app.bsky.feed.post/3kabc",
                "author":{"handle":"alice.bsky.social","displayName":"Alice"},
                "record":{"text":"Java agents are great"},
                "likeCount":42
              }}
            ]}
            """;

    private WireMockServer wireMock;
    private BlueskySearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        String baseUrl = "http://localhost:" + wireMock.port();
        SocialProperties props = new SocialProperties(5, 0,
                new SocialProperties.Bluesky(baseUrl, List.of("alice.bsky.social")),
                new SocialProperties.Mastodon(baseUrl, List.of()));
        adapter = new BlueskySearchAdapter(new ObjectMapper(), props);
        Field f = BlueskySearchAdapter.class.getDeclaredField("restClient");
        f.setAccessible(true);
        f.set(adapter, RestClient.builder().build());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchesAndParsesAuthorFeedIntoSocialPosts() {
        wireMock.stubFor(get(urlPathEqualTo("/xrpc/app.bsky.feed.getAuthorFeed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FEED_RESPONSE)));

        List<SocialPost> posts = adapter.fetchPosts(5);

        assertThat(posts).hasSize(1);
        SocialPost p = posts.getFirst();
        assertThat(p.network()).isEqualTo("Bluesky");
        assertThat(p.author()).isEqualTo("alice.bsky.social");
        assertThat(p.text()).isEqualTo("Java agents are great");
        assertThat(p.url()).isEqualTo("https://bsky.app/profile/alice.bsky.social/post/3kabc");
        assertThat(p.likeCount()).isEqualTo(42);
    }

    @Test
    void returnsEmptyWhenNoHandlesConfigured() throws Exception {
        SocialProperties props = new SocialProperties(5, 0,
                new SocialProperties.Bluesky("http://localhost:" + wireMock.port(), List.of()),
                new SocialProperties.Mastodon("http://localhost:" + wireMock.port(), List.of()));
        BlueskySearchAdapter noHandles = new BlueskySearchAdapter(new ObjectMapper(), props);

        assertThat(noHandles.fetchPosts(5)).isEmpty();
    }
}
