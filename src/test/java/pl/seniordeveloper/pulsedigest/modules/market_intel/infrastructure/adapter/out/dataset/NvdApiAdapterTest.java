package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.NvdVulnerability;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NvdApiAdapterTest {

    private static final String NVD_RESPONSE = """
            {
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2026-12345",
                    "published": "2099-05-06T10:00:00.000",
                    "descriptions": [
                      {"lang": "en", "value": "Remote code execution in Apache Log4j."},
                      {"lang": "pl", "value": "Zdalne wykonanie kodu w Apache Log4j."}
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
                },
                {
                  "cve": {
                    "id": "CVE-2026-12346",
                    "published": "2099-05-06T09:00:00.000",
                    "descriptions": [
                      {"lang": "en", "value": "XSS vulnerability in React component."}
                    ],
                    "metrics": {
                      "cvssMetricV31": [
                        {
                          "cvssData": {
                            "baseScore": 7.2,
                            "baseSeverity": "HIGH"
                          }
                        }
                      ]
                    },
                    "configurations": [
                      {
                        "nodes": [
                          {
                            "cpeMatch": [
                              {"criteria": "cpe:2.3:a:facebook:react:18.0:*:*:*:*:*:*:*"}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                },
                {
                  "cve": {
                    "id": "CVE-2026-12347",
                    "published": "2099-05-06T08:00:00.000",
                    "descriptions": [],
                    "metrics": {
                      "cvssMetricV31": [
                        {
                          "cvssData": {
                            "baseScore": 5.5,
                            "baseSeverity": "MEDIUM"
                          }
                        }
                      ]
                    },
                    "configurations": []
                  }
                }
              ]
            }
            """;

    private NvdApiAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.NvdProperties props =
                new ReportProperties.NvdProperties(
                        "https://services.nvd.nist.gov/rest/json/cves/2.0",
                        20,
                        48,
                        List.of("CRITICAL", "HIGH")
                );
        adapter = new NvdApiAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesCriticalAndHighVulnerabilities() {
        List<NvdVulnerability> vulns = adapter.parseVulnerabilities(NVD_RESPONSE);

        assertThat(vulns).hasSize(2);
        assertThat(vulns.get(0).cveId()).isEqualTo("CVE-2026-12345");
        assertThat(vulns.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(vulns.get(0).cvssScore()).isEqualTo(9.8);
        assertThat(vulns.get(0).description()).isEqualTo("Remote code execution in Apache Log4j.");
        assertThat(vulns.get(0).affectedProducts()).contains("apache:log4j");
        assertThat(vulns.get(0).url()).isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-12345");
    }

    @Test
    void filtersOutMediumSeverityVulnerabilities() {
        List<NvdVulnerability> vulns = adapter.parseVulnerabilities(NVD_RESPONSE);

        assertThat(vulns).extracting(NvdVulnerability::cveId)
                .doesNotContain("CVE-2026-12347");
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<NvdVulnerability> vulns = adapter.parseVulnerabilities("NOT JSON");
        assertThat(vulns).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyVulnerabilities() {
        String json = "{\"vulnerabilities\": []}";
        List<NvdVulnerability> vulns = adapter.parseVulnerabilities(json);
        assertThat(vulns).isEmpty();
    }
}
