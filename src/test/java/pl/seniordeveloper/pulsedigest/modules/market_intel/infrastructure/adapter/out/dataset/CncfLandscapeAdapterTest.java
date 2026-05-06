package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CncfLandscapeAdapterTest {

    private static final String COMMITS_JSON = """
            [
              {
                "commit": {
                  "message": "Promote OpenTelemetry to graduated status\\n\\nCNCF TOC vote passed.",
                  "committer": {"date": "2099-05-06T10:00:00.000Z"}
                }
              },
              {
                "commit": {
                  "message": "Add the new sandbox project KubeFox\\n\\nInitial submission.",
                  "committer": {"date": "2099-05-05T15:00:00.000Z"}
                }
              },
              {
                "commit": {
                  "message": "Fix typo in README.md\\n\\nNo project changes.",
                  "committer": {"date": "2099-05-05T09:00:00.000Z"}
                }
              }
            ]
            """;

    private CncfLandscapeAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.CncfLandscapeProperties props =
                new ReportProperties.CncfLandscapeProperties(
                        "https://api.github.com/repos/cncf/landscape/commits",
                        7,
                        List.of("sandbox", "incubating", "graduated", "archived")
                );
        adapter = new CncfLandscapeAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesProjectStatusChanges() {
        List<CncfProjectUpdate> changes = adapter.parseLandscapeChanges(COMMITS_JSON);

        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).status()).isEqualTo("Graduated");
        assertThat(changes.get(1).status()).isEqualTo("Sandbox");
    }

    @Test
    void filtersOutNonLandscapeCommits() {
        List<CncfProjectUpdate> changes = adapter.parseLandscapeChanges(COMMITS_JSON);

        assertThat(changes).hasSize(2);
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<CncfProjectUpdate> changes = adapter.parseLandscapeChanges("NOT JSON");
        assertThat(changes).isEmpty();
    }
}
