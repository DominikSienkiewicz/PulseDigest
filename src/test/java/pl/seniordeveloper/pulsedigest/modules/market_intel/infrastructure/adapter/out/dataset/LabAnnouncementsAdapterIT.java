package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LabAnnouncementsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LabAnnouncementsProperties.Source;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LabAnnouncementsProperties.Strategy;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabAnnouncementsAdapterIT {

    private WireMockServer wireMock;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    private LabAnnouncementsAdapter adapterWith(Source... sources) throws Exception {
        LabAnnouncementsProperties props = new LabAnnouncementsProperties(48, 10, List.of(sources));
        LabAnnouncementsAdapter adapter = new LabAnnouncementsAdapter(props);
        Field restClientField = LabAnnouncementsAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, RestClient.builder().build());
        return adapter;
    }

    private static ResponseDefinitionBuilder html(String body) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody(body);
    }

    @Test
    void sanityStrategyParsesInlineCmsDataWithinWindow() throws Exception {
        String recentIso = LocalDateTime.now().minusHours(3) + "Z";
        String staleIso = LocalDateTime.now().minusDays(30) + "Z";
        String listing = ("<html><body><script>window.__d = \""
                + "\\\"publishedOn\\\":\\\"%s\\\",\\\"slug\\\":{\\\"current\\\":\\\"fresh-post\\\"},"
                + "\\\"summary\\\":\\\"Fresh summary\\\",\\\"title\\\":\\\"Fresh title\\\""
                + "\\\"publishedOn\\\":\\\"%s\\\",\\\"slug\\\":{\\\"current\\\":\\\"stale-post\\\"},"
                + "\\\"summary\\\":\\\"Stale summary\\\",\\\"title\\\":\\\"Stale title\\\""
                + "\";</script></body></html>").formatted(recentIso, staleIso);
        wireMock.stubFor(get(urlPathEqualTo("/news")).willReturn(html(listing)));

        LabAnnouncementsAdapter adapter = adapterWith(
                new Source("Anthropic News", Strategy.SANITY, baseUrl + "/news", null));
        List<LabAnnouncement> result = adapter.fetchAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Fresh title");
        assertThat(result.getFirst().summary()).isEqualTo("Fresh summary");
        assertThat(result.getFirst().source()).isEqualTo("Anthropic News");
        assertThat(result.getFirst().url()).isEqualTo(baseUrl + "/news/fresh-post");
    }

    @Test
    void jsonLdStrategyFetchesListingThenPerPostDate() throws Exception {
        String recentIso = LocalDate.now() + "T09:00:00+00:00";
        String listing = "<html><body>"
                + "<a href=\"/blog/recent-post\">Recent</a>"
                + "<a href=\"/blog/old-post\">Old</a>"
                + "</body></html>";
        String recentPost = ("<html><head>"
                + "<meta property=\"og:title\" content=\"Recent JSONLD post\"/>"
                + "<meta name=\"description\" content=\"A JSONLD summary.\"/>"
                + "<script type=\"application/ld+json\">{\"datePublished\": \"%s\"}</script>"
                + "</head></html>").formatted(recentIso);
        String oldPost = "<html><head>"
                + "<meta property=\"og:title\" content=\"Old post\"/>"
                + "<script type=\"application/ld+json\">{\"datePublished\": \"2024-01-01\"}</script>"
                + "</head></html>";
        wireMock.stubFor(get(urlPathEqualTo("/blog")).willReturn(html(listing)));
        wireMock.stubFor(get(urlPathEqualTo("/blog/recent-post")).willReturn(html(recentPost)));
        wireMock.stubFor(get(urlPathEqualTo("/blog/old-post")).willReturn(html(oldPost)));

        LabAnnouncementsAdapter adapter = adapterWith(
                new Source("Claude Blog", Strategy.JSONLD, baseUrl + "/blog", "href=\"(/blog/[a-z0-9-]+)\""));
        List<LabAnnouncement> result = adapter.fetchAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Recent JSONLD post");
        assertThat(result.getFirst().summary()).isEqualTo("A JSONLD summary.");
        assertThat(result.getFirst().url()).isEqualTo(baseUrl + "/blog/recent-post");
    }

    @Test
    void openAiDevStrategyParsesCardsInlineWithYearlessDate() throws Exception {
        String recentShortDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));
        String listing = ("<html><body>"
                + "<a href=\"/blog/fresh-openai-post\">"
                + "<div class=\"text-sm text-secondary\">%s</div>"
                + "<div class=\"text-xl\"><div class=\"line-clamp-2\">Fresh OpenAI post</div></div>"
                + "<p class=\"line-clamp-3\">OpenAI description.</p>"
                + "</a>"
                + "<a href=\"/blog/stale-openai-post\">"
                + "<div class=\"text-sm text-secondary\">Jan 1</div>"
                + "<div class=\"text-xl\"><div class=\"line-clamp-2\">Stale OpenAI post</div></div>"
                + "<p class=\"line-clamp-3\">Old.</p>"
                + "</a></body></html>").formatted(recentShortDate);
        wireMock.stubFor(get(urlPathEqualTo("/blog")).willReturn(html(listing)));

        LabAnnouncementsAdapter adapter = adapterWith(
                new Source("OpenAI Dev Blog", Strategy.OPENAI_DEV, baseUrl + "/blog", null));
        List<LabAnnouncement> result = adapter.fetchAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Fresh OpenAI post");
        assertThat(result.getFirst().summary()).isEqualTo("OpenAI description.");
        assertThat(result.getFirst().url()).isEqualTo(baseUrl + "/blog/fresh-openai-post");
    }

    @Test
    void throwsOnlyWhenEverySourceFails() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/news")).willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(get(urlPathEqualTo("/blog")).willReturn(aResponse().withStatus(503)));

        LabAnnouncementsAdapter adapter = adapterWith(
                new Source("Anthropic News", Strategy.SANITY, baseUrl + "/news", null),
                new Source("OpenAI Dev Blog", Strategy.OPENAI_DEV, baseUrl + "/blog", null));

        assertThatThrownBy(adapter::fetchAnnouncements)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("All 2 lab announcement sources failed");
    }

    @Test
    void singleSourceFailureStillReturnsResultsFromHealthySource() throws Exception {
        String recentShortDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));
        String listing = ("<html><body><a href=\"/blog/ok-post\">"
                + "<div class=\"text-sm text-secondary\">%s</div>"
                + "<div class=\"text-xl\"><div class=\"line-clamp-2\">OK post</div></div>"
                + "<p class=\"line-clamp-3\">desc</p></a></body></html>").formatted(recentShortDate);
        wireMock.stubFor(get(urlPathEqualTo("/news")).willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(get(urlPathEqualTo("/blog")).willReturn(html(listing)));

        LabAnnouncementsAdapter adapter = adapterWith(
                new Source("Anthropic News", Strategy.SANITY, baseUrl + "/news", null),
                new Source("OpenAI Dev Blog", Strategy.OPENAI_DEV, baseUrl + "/blog", null));
        List<LabAnnouncement> result = adapter.fetchAnnouncements();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().source()).isEqualTo("OpenAI Dev Blog");
    }
}
