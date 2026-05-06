package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenceTalksAdapterTest {

    private static final String SEARCH_JSON = """
            {
              "items": [
                {
                  "id": {"videoId": "abc123"},
                  "snippet": {
                    "title": "Virtual Threads Deep Dive",
                    "publishedAt": "2099-05-06T10:00:00.000Z"
                  }
                },
                {
                  "id": {"videoId": "old456"},
                  "snippet": {
                    "title": "Old Talk from Last Year",
                    "publishedAt": "2020-01-01T10:00:00.000Z"
                  }
                },
                {
                  "id": {"videoId": "empty789"},
                  "snippet": {
                    "title": "",
                    "publishedAt": "2099-05-05T15:00:00.000Z"
                  }
                }
              ]
            }
            """;

    private ConferenceTalksAdapter adapter;

    @BeforeEach
    void setUp() {
        ReportProperties.ConferenceTalksProperties props =
                new ReportProperties.ConferenceTalksProperties(
                        "https://www.googleapis.com/youtube/v3/search",
                        "test-api-key",
                        7,
                        10,
                        List.of(
                                new ReportProperties.ConferenceTalksProperties.ChannelConfig(
                                        "SpringDeveloper", "Spring I/O", "UC_test"))
                );
        adapter = new ConferenceTalksAdapter(props, new ObjectMapper());
    }

    @Test
    void parsesSearchResultsWithinLookback() {
        List<ConferenceTalk> talks = adapter.parseSearchResults(
                SEARCH_JSON, "SpringDeveloper", "Spring I/O");

        assertThat(talks).hasSize(1);
        assertThat(talks.get(0).title()).isEqualTo("Virtual Threads Deep Dive");
        assertThat(talks.get(0).channelName()).isEqualTo("SpringDeveloper");
        assertThat(talks.get(0).conferenceName()).isEqualTo("Spring I/O");
        assertThat(talks.get(0).url()).isEqualTo("https://www.youtube.com/watch?v=abc123");
    }

    @Test
    void filtersOutTalksOutsideLookbackWindow() {
        List<ConferenceTalk> talks = adapter.parseSearchResults(
                SEARCH_JSON, "SpringDeveloper", "Spring I/O");

        assertThat(talks).extracting(ConferenceTalk::title)
                .doesNotContain("Old Talk from Last Year");
    }

    @Test
    void filtersOutEmptyTitles() {
        List<ConferenceTalk> talks = adapter.parseSearchResults(
                SEARCH_JSON, "SpringDeveloper", "Spring I/O");

        assertThat(talks).hasSize(1);
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<ConferenceTalk> talks = adapter.parseSearchResults(
                "NOT JSON", "Channel", "Conference");
        assertThat(talks).isEmpty();
    }
}
