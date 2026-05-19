package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.NvdVulnerability;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.NvdProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NvdApiAdapterIT {

    private static final String NVD_RESPONSE = """
            {
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2026-12345",
                    "published": "2099-05-06T10:00:00.000",
                    "descriptions": [
                      {"lang": "en", "value": "Remote code execution in Apache Log4j."}
                    ],
                    "metrics": {
                      "cvssMetricV31": [
                        {
                          "cvssData": {
                            "baseScore": 9.8,
                            "baseSeverity": "CRITICAL"
                          }
                        }
                      ]
                    },
                    "configurations": [
                      {
                        "nodes": [
                          {
                            "cpeMatch": [
                              {"criteria": "cpe:2.3:a:apache:log4j:2.0:*:*:*:*:*:*:*"}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
            """;

    private WireMockServer wireMock;
    private NvdApiAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        NvdProperties props =
                new NvdProperties(
                        "http://localhost:" + wireMock.port(),
                        20,
                        48,
                        List.of("CRITICAL", "HIGH"));
        adapter = new NvdApiAdapter(props, new ObjectMapper());

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();

        Field restClientField = NvdApiAdapter.class.getDeclaredField("restClient");
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
    void fetchesAndParsesVulnerabilities() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NVD_RESPONSE)));

        List<NvdVulnerability> vulns = adapter.fetchNvdVulnerabilities();

        assertThat(vulns).hasSize(1);
        assertThat(vulns.get(0).cveId()).isEqualTo("CVE-2026-12345");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/")))).hasSize(1);
    }

    @Test
    void propagatesHttpErrorOnApiReturns503() {
        wireMock.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.fetchNvdVulnerabilities())
                .isInstanceOf(RuntimeException.class);
    }
}
