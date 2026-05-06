package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnologyRadarAdapterTest {

    private static final String RADAR_JSON = """
            [
              {
                "name": "WebAssembly",
                "ring": "adopt",
                "quadrant": "platforms",
                "description": "Browser-native code execution.",
                "url": "https://www.thoughtworks.com/radar/platforms/webassembly"
              },
              {
                "name": "eBPF",
                "ring": "trial",
                "quadrant": "techniques",
                "description": "",
                "url": ""
              },
              {
                "name": "",
                "ring": "adopt",
                "quadrant": "tools",
                "description": "Empty name entry.",
                "url": ""
              }
            ]
            """;

    private TechnologyRadarAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.TechnologyRadarProperties props =
                new ReportProperties.TechnologyRadarProperties(
                        "https://raw.githubusercontent.com",
                        "/setchy/thoughtworks-tech-radar-volumes/main/volumes/json/"
                                + "Thoughtworks%20Technology%20Radar%20Volume%2034%20(Apr%202026).json",
                        6
                );
        adapter = new TechnologyRadarAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesRadarEntriesAndCapitalizesRings() {
        List<RadarEntry> entries = adapter.parseRadarEntries(RADAR_JSON);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).name()).isEqualTo("WebAssembly");
        assertThat(entries.get(0).ring()).isEqualTo("Adopt");
        assertThat(entries.get(0).quadrant()).isEqualTo("Platforms");
        assertThat(entries.get(0).url()).isEqualTo("https://www.thoughtworks.com/radar/platforms/webassembly");
    }

    @Test
    void fallsBackToDefaultUrlWhenEmpty() {
        List<RadarEntry> entries = adapter.parseRadarEntries(RADAR_JSON);

        assertThat(entries.get(1).url()).isEqualTo("https://www.thoughtworks.com/radar");
    }

    @Test
    void filtersOutBlankNames() {
        List<RadarEntry> entries = adapter.parseRadarEntries(RADAR_JSON);

        assertThat(entries).hasSize(2);
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<RadarEntry> entries = adapter.parseRadarEntries("NOT JSON");
        assertThat(entries).isEmpty();
    }
}
