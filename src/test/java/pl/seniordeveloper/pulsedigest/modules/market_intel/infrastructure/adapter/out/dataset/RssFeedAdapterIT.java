package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RssProperties;

import java.lang.reflect.Field;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RssFeedAdapterIT {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchAllParsesRssAndFiltersInvalidItems() throws Exception {
        String recentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        String longDescription = "x".repeat(400);
        wireMock.stubFor(get(urlPathEqualTo("/rss"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/rss+xml")
                        .withBody("""
                                <rss><channel>
                                  <item>
                                    <title>Recent Java item</title>
                                    <link>https://example.com/recent</link>
                                    <description>%s</description>
                                    <pubDate>%s</pubDate>
                                  </item>
                                  <item>
                                    <title>Old item</title>
                                    <link>https://example.com/old</link>
                                    <description>old</description>
                                    <pubDate>Wed, 01 Jan 2020 10:00:00 GMT</pubDate>
                                  </item>
                                  <item>
                                    <title></title>
                                    <link>https://example.com/missing-title</link>
                                  </item>
                                </channel></rss>
                                """.formatted(longDescription, recentDate))));

        List<RssItem> items = adapter("/rss").fetchAll();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().title()).isEqualTo("Recent Java item");
        assertThat(items.getFirst().description()).hasSize(300);
        assertThat(items.getFirst().feedName()).isEqualTo("Test Feed");
    }

    @Test
    void fetchAllParsesAtomAlternateLinkAndMissingDate() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/atom"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/atom+xml")
                        .withBody("""
                                <feed>
                                  <entry>
                                    <title>Atom item</title>
                                    <link rel="self" href="https://example.com/feed-entry"/>
                                    <link rel="alternate" href="https://example.com/atom"/>
                                    <summary>Atom summary</summary>
                                  </entry>
                                </feed>
                                """)));

        List<RssItem> items = adapter("/atom").fetchAll();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().url()).isEqualTo("https://example.com/atom");
        assertThat(items.getFirst().description()).isEqualTo("Atom summary");
    }

    @Test
    void fetchAllReturnsEmptyForBlankFeed() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/blank"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        assertThat(adapter("/blank").fetchAll()).isEmpty();
    }

    @Test
    void includesItemsWithinConfiguredLookbackWindow() throws Exception {
        String date40hAgo = ZonedDateTime.now(ZoneOffset.UTC).minusHours(40)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        wireMock.stubFor(get(urlPathEqualTo("/window"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/rss+xml")
                        .withBody("""
                                <rss><channel>
                                  <item>
                                    <title>40h old item</title>
                                    <link>https://example.com/40h</link>
                                    <description>d</description>
                                    <pubDate>%s</pubDate>
                                  </item>
                                </channel></rss>
                                """.formatted(date40hAgo))));

        List<RssItem> items = adapter("/window").fetchAll();

        // 40h-old item is outside the old hardcoded 24h window but inside the configured 80h window.
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().title()).isEqualTo("40h old item");
    }

    private RssFeedAdapter adapter(String path) throws Exception {
        RssProperties rss = new RssProperties(
                5,
                List.of(new RssProperties.FeedConfig(
                        "Test Feed",
                        "http://localhost:" + wireMock.port() + path)),
                80);
        RssFeedAdapter adapter = new RssFeedAdapter(rss);
        Field restClientField = RssFeedAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, RestClient.builder().build());
        return adapter;
    }
}
