package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DbEngineRanking;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DbEnginesProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class DbEnginesAdapterIT {

    private static final String RANKING_HTML = """
            <html><body><table class="dbi">
            <tr><td>1.</td><td><a href="/en/ranking/postgresql">PostgreSQL</a></td>
            <td>1250.5</td><td>+12.3</td><td>-2.1</td></tr>
            </table></body></html>
            """;

    private WireMockServer wireMock;
    private DbEnginesAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        DbEnginesProperties props =
                new DbEnginesProperties(
                        "http://localhost:" + wireMock.port(),
                        7,
                        5);
        adapter = new DbEnginesAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "text/html, application/xhtml+xml")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = DbEnginesAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesRankings() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(RANKING_HTML)));

        List<DbEngineRanking> rankings = adapter.fetchDbEngineRankings();

        assertThat(rankings).hasSize(1);
        assertThat(rankings.get(0).dbName()).isEqualTo("PostgreSQL");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        List<DbEngineRanking> rankings = adapter.fetchDbEngineRankings();

        assertThat(rankings).isEmpty();
    }
}
