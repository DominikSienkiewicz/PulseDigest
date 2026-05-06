package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAdvisoryAdapterTest {

    private SecurityAdvisoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.SecurityAdvisoriesProperties props =
                new ReportProperties.SecurityAdvisoriesProperties(
                        "https://api.github.com/advisories",
                        50,
                        24 * 365 * 80,
                        List.of("HIGH", "CRITICAL"),
                        List.of("maven", "npm", "actions"));
        adapter = new SecurityAdvisoryAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesHighSeverityMavenAdvisoryWithinLookback() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
        String json = """
                [{
                  "ghsa_id": "GHSA-aaaa-bbbb-cccc",
                  "summary": "RCE in foo",
                  "severity": "high",
                  "published_at": "%s",
                  "html_url": "https://github.com/advisories/GHSA-aaaa-bbbb-cccc",
                  "vulnerabilities": [
                    {"package": {"ecosystem": "maven", "name": "com.foo:bar"}}
                  ]
                }]
                """.formatted(recent);

        List<SecurityAdvisory> advisories = adapter.parseAdvisories(json);

        assertThat(advisories).hasSize(1);
        SecurityAdvisory a = advisories.get(0);
        assertThat(a.ghsaId()).isEqualTo("GHSA-aaaa-bbbb-cccc");
        assertThat(a.severity()).isEqualTo("HIGH");
        assertThat(a.affectedEcosystems()).containsExactly("maven");
    }

    @Test
    void filtersOutLowSeverityAdvisories() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
        String json = """
                [{
                  "ghsa_id": "GHSA-low-x",
                  "summary": "Minor issue",
                  "severity": "low",
                  "published_at": "%s",
                  "html_url": "https://github.com/advisories/GHSA-low-x",
                  "vulnerabilities": [{"package": {"ecosystem": "npm"}}]
                }]
                """.formatted(recent);

        List<SecurityAdvisory> advisories = adapter.parseAdvisories(json);

        assertThat(advisories).isEmpty();
    }

    @Test
    void filtersOutAdvisoriesForIrrelevantEcosystems() {
        String recent = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
        String json = """
                [{
                  "ghsa_id": "GHSA-other-y",
                  "summary": "Critical in unrelated ecosystem",
                  "severity": "critical",
                  "published_at": "%s",
                  "html_url": "https://github.com/advisories/GHSA-other-y",
                  "vulnerabilities": [{"package": {"ecosystem": "swift"}}]
                }]
                """.formatted(recent);

        List<SecurityAdvisory> advisories = adapter.parseAdvisories(json);

        assertThat(advisories).isEmpty();
    }

    @Test
    void filtersOutAdvisoriesOutsideLookbackWindow() {
        ReportProperties.SecurityAdvisoriesProperties shortLookback =
                new ReportProperties.SecurityAdvisoriesProperties(
                        "https://api.github.com/advisories",
                        50,
                        1,
                        List.of("HIGH", "CRITICAL"),
                        List.of("maven"));
        SecurityAdvisoryAdapter shortAdapter = new SecurityAdvisoryAdapter(shortLookback, new ObjectMapper());

        String json = """
                [{
                  "ghsa_id": "GHSA-old-z",
                  "summary": "Old advisory",
                  "severity": "high",
                  "published_at": "2020-01-01T00:00:00Z",
                  "html_url": "https://github.com/advisories/GHSA-old-z",
                  "vulnerabilities": [{"package": {"ecosystem": "maven"}}]
                }]
                """;

        List<SecurityAdvisory> advisories = shortAdapter.parseAdvisories(json);

        assertThat(advisories).isEmpty();
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<SecurityAdvisory> advisories = adapter.parseAdvisories("NOT JSON");
        assertThat(advisories).isEmpty();
    }
}
