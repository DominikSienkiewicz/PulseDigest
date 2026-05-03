package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GithubReleasesAdapterIT {

    private static final String RELEASES_RESPONSE = """
            [
              {
                "tag_name": "v3.5.0",
                "name": "Spring Boot 3.5.0",
                "html_url": "https://github.com/spring-projects/spring-boot/releases/tag/v3.5.0",
                "body": "Virtual thread improvements and Spring AI updates",
                "published_at": "2099-05-01T08:00:00Z",
                "prerelease": false,
                "draft": false
              }
            ]
            """;

    private WireMockServer wireMock;
    private GithubReleasesAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ReportProperties.GithubReleasesProperties props =
                new ReportProperties.GithubReleasesProperties(
                        List.of("spring-projects/spring-boot"), 48);
        adapter = new GithubReleasesAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = GithubReleasesAdapter.class.getDeclaredField("restClient");
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
    void fetchesReleasesAndFiltersPrerelease() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-boot/releases"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RELEASES_RESPONSE)));

        List<SoftwareRelease> releases = adapter.fetchLatestReleases();

        assertThat(releases).hasSize(1);
        assertThat(releases.get(0).version()).isEqualTo("v3.5.0");
        assertThat(wireMock.findAll(getRequestedFor(
                urlPathEqualTo("/repos/spring-projects/spring-boot/releases")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-boot/releases"))
                .willReturn(aResponse().withStatus(503)));

        List<SoftwareRelease> releases = adapter.fetchLatestReleases();

        assertThat(releases).isEmpty();
    }
}
