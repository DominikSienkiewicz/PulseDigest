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

class MastodonSearchAdapterIT {

    private static final String TAG_RESPONSE = """
            [
              {
                "url":"https://fosstodon.org/@bob/123",
                "content":"<p>Quarkus <b>native</b> image &amp; GraalVM</p>",
                "account":{"acct":"bob","display_name":"Bob"},
                "favourites_count":30
              }
            ]
            """;

    private WireMockServer wireMock;
    private MastodonSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        String baseUrl = "http://localhost:" + wireMock.port();
        SocialProperties props = new SocialProperties(5, 0,
                new SocialProperties.Bluesky(baseUrl, List.of()),
                new SocialProperties.Mastodon(baseUrl, List.of("java")));
        adapter = new MastodonSearchAdapter(new ObjectMapper(), props);
        Field f = MastodonSearchAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesTagTimelineStrippingHtml() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/timelines/tag/java"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TAG_RESPONSE)));

        List<SocialPost> posts = adapter.fetchPosts(5);

        assertThat(posts).hasSize(1);
        SocialPost p = posts.getFirst();
        assertThat(p.network()).isEqualTo("Mastodon");
        assertThat(p.author()).isEqualTo("bob");
        assertThat(p.text()).isEqualTo("Quarkus native image & GraalVM");   // HTML stripped, entity decoded
        assertThat(p.url()).isEqualTo("https://fosstodon.org/@bob/123");
        assertThat(p.likeCount()).isEqualTo(30);
    }
}
