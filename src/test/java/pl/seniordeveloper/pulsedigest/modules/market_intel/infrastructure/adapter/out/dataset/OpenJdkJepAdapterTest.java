package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenJdkProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenJdkJepAdapterTest {

    private static final String COMMITS_JSON = """
            [
              {
                "commit": {
                  "message": "JEP-456: Virtual Threads for Platform Threads\\n\\nIntegrated into JDK 26.",
                  "committer": {"date": "2099-05-06T10:00:00.000Z"}
                }
              },
              {
                "commit": {
                  "message": "Proposed to Target JEP 789 Value Objects for Java\\n\\nReviewed-by: briangoetz",
                  "committer": {"date": "2099-05-05T15:00:00.000Z"}
                }
              },
              {
                "commit": {
                  "message": "Fix typo in JEP-456 documentation\\n\\nTrivial fix.",
                  "committer": {"date": "2099-05-06T12:00:00.000Z"}
                }
              },
              {
                "commit": {
                  "message": "Revert accidental change\\n\\nNo JEP reference.",
                  "committer": {"date": "2099-05-05T09:00:00.000Z"}
                }
              }
            ]
            """;

    private OpenJdkJepAdapter adapter;

    @BeforeEach
    void setUp() {
        OpenJdkProperties props =
                new OpenJdkProperties(
                        "https://api.github.com/repos/openjdk/jdk/commits",
                        7,
                        List.of("Candidate", "Proposed to Target", "Integrated", "Delivered")
                );
        adapter = new OpenJdkJepAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesJepUpdatesWithStatusChanges() {
        List<JepUpdate> updates = adapter.parseJepUpdates(COMMITS_JSON);

        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).jepId()).isEqualTo("JEP-456");
        assertThat(updates.get(0).status()).isEqualTo("Integrated");
        assertThat(updates.get(0).url()).isEqualTo("https://openjdk.org/jeps/456");
    }

    @Test
    void deduplicatesByJepId() {
        List<JepUpdate> updates = adapter.parseJepUpdates(COMMITS_JSON);

        assertThat(updates).extracting(JepUpdate::jepId)
                .containsOnlyOnce("JEP-456");
    }

    @Test
    void filtersOutCommitsWithoutJepReference() {
        List<JepUpdate> updates = adapter.parseJepUpdates(COMMITS_JSON);

        assertThat(updates).extracting(JepUpdate::jepId)
                .doesNotContain("No JEP reference");
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<JepUpdate> updates = adapter.parseJepUpdates("NOT JSON");
        assertThat(updates).isEmpty();
    }
}
