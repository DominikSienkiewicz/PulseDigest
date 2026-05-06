package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PackageTrend;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LibrariesIoAdapterTest {

    private static final String TRENDS_JSON = """
            [
              {
                "name": "langchain4j",
                "platform": "maven",
                "description": "Java LLM framework",
                "stars": 12000,
                "dependents_count": 450,
                "homepage": "https://github.com/langchain4j/langchain4j",
                "repository_url": "",
                "latest_release_published_at": "2099-05-01T12:00:00.000Z"
              },
              {
                "name": "ollama",
                "platform": "pypi",
                "description": "",
                "stars": 500,
                "dependents_count": 80,
                "homepage": "",
                "repository_url": "https://github.com/ollama/ollama-python",
                "latest_release_published_at": ""
              },
              {
                "name": "",
                "platform": "npm",
                "description": "",
                "stars": 0,
                "dependents_count": 0,
                "homepage": "",
                "repository_url": "",
                "latest_release_published_at": ""
              }
            ]
            """;

    private LibrariesIoAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.LibrariesIoProperties props =
                new ReportProperties.LibrariesIoProperties(
                        "https://libraries.io/api/search",
                        "test-api-key",
                        20,
                        List.of("maven", "npm", "pypi"),
                        90
                );
        adapter = new LibrariesIoAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesPackageTrendsWithHomepage() {
        List<PackageTrend> trends = adapter.parsePackageTrends(TRENDS_JSON);

        assertThat(trends).hasSize(2);
        assertThat(trends.get(0).name()).isEqualTo("langchain4j");
        assertThat(trends.get(0).platform()).isEqualTo("maven");
        assertThat(trends.get(0).stars()).isEqualTo(12000);
        assertThat(trends.get(0).dependentProjects()).isEqualTo(450);
        assertThat(trends.get(0).url()).isEqualTo("https://github.com/langchain4j/langchain4j");
    }

    @Test
    void fallsBackToRepositoryUrlWhenHomepageIsEmpty() {
        List<PackageTrend> trends = adapter.parsePackageTrends(TRENDS_JSON);

        assertThat(trends.get(1).name()).isEqualTo("ollama");
        assertThat(trends.get(1).url()).isEqualTo("https://github.com/ollama/ollama-python");
    }

    @Test
    void filtersOutBlankNames() {
        List<PackageTrend> trends = adapter.parsePackageTrends(TRENDS_JSON);

        assertThat(trends).hasSize(2);
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<PackageTrend> trends = adapter.parsePackageTrends("NOT JSON");
        assertThat(trends).isEmpty();
    }
}
