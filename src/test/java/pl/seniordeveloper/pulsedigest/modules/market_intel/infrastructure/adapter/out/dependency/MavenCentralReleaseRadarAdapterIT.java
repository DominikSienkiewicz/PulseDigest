package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class MavenCentralReleaseRadarAdapterIT {

    private WireMockServer wireMock;
    private MavenCentralReleaseRadarAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        adapter = new MavenCentralReleaseRadarAdapter(new ObjectMapper());
        ReflectionTestUtils.setField(adapter, "registryUrl", "http://localhost:" + wireMock.port());
        adapter.init();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void reportsADependencyPinnedBehindItsLatestRelease() {
        stubLatest("3.5.3");

        List<DependencyRelease> behind = adapter.outdated(profileOf(pinned("3.5.0")));

        assertThat(behind).singleElement().satisfies(release -> {
            assertThat(release.latestVersion()).isEqualTo("3.5.3");
            assertThat(release.summary())
                    .isEqualTo("org.springframework.boot:spring-boot-starter 3.5.0 → 3.5.3");
        });
    }

    @Test
    void saysNothingWhenThePinIsAlreadyCurrent() {
        stubLatest("3.5.0");

        assertThat(adapter.outdated(profileOf(pinned("3.5.0")))).isEmpty();
    }

    /**
     * The worked example from this very repository: it pins Spring Boot {@code 4.1.0-SNAPSHOT} while
     * Central's latest release is 3.5.x. Comparing them would report going *backwards* as an upgrade,
     * so a pre-release pin is skipped — and skipped without a lookup, since the answer cannot be
     * useful either way.
     */
    @Test
    void neverNagsAboutAPreReleasePin() {
        stubLatest("3.5.3");

        assertThat(adapter.outdated(profileOf(pinned("4.1.0-SNAPSHOT")))).isEmpty();
        assertThat(wireMock.getAllServeEvents())
                .as("a pre-release pin must not even cost an HTTP call")
                .isEmpty();
    }

    @Test
    void aCoordinateWithNoPinnedVersionIsNotCheckedAtAll() {
        stubLatest("3.5.3");

        assertThat(adapter.outdated(profileOf(pinned("")))).isEmpty();
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    void anUnreachableRegistryCostsTheRadarNothingBeyondThatArtefact() {
        wireMock.stubFor(get(urlPathEqualTo("/solrsearch/select"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(adapter.outdated(profileOf(pinned("3.5.0")))).isEmpty();
    }

    @Test
    void anArtefactTheRegistryDoesNotKnowIsSkipped() {
        wireMock.stubFor(get(urlPathEqualTo("/solrsearch/select")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"response\":{\"docs\":[]}}")));

        assertThat(adapter.outdated(profileOf(pinned("3.5.0")))).isEmpty();
    }

    private void stubLatest(String latestVersion) {
        wireMock.stubFor(get(urlPathEqualTo("/solrsearch/select")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"response\":{\"docs\":[{\"id\":\"x\",\"latestVersion\":\""
                        + latestVersion + "\"}]}}")));
    }

    private static DependencyCoordinate pinned(String version) {
        return new DependencyCoordinate("org.springframework.boot", "spring-boot-starter", version, "test");
    }

    private static DependencyProfile profileOf(DependencyCoordinate coordinate) {
        return new DependencyProfile(List.of(coordinate), true, List.of("test"));
    }
}
