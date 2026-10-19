package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** N48. The grouping, and the one cost it has: editions stored before it existed. */
class RawCountsTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void theBuilderNamesEveryCountSoNoTwoCanBeTransposed() {
        // The whole reason this type exists. Five positional ints is the one shape in this codebase
        // where swapping two arguments compiles cleanly and corrupts a number nobody will question.
        RawCounts counts = RawCounts.builder()
                .tweets(214)
                .hackerNews(118)
                .github(60)
                .rss(173)
                .reddit(86)
                .build();

        assertThat(counts.tweets()).isEqualTo(214);
        assertThat(counts.hackerNews()).isEqualTo(118);
        assertThat(counts.github()).isEqualTo(60);
        assertThat(counts.rss()).isEqualTo(173);
        assertThat(counts.reddit()).isEqualTo(86);
        assertThat(counts.total()).isEqualTo(651);
    }

    @Test
    void anUnsetCountIsZeroRatherThanAnError() {
        assertThat(RawCounts.builder().github(3).build().total()).isEqualTo(3);
    }

    @Test
    void anEditionStoredBeforeThisRecordExistedDegradesToZeroInsteadOfCrashing() throws Exception {
        // edition_inputs holds research payloads written with the five flat fields this record
        // replaced. `--replay-prompt` has to keep working on those: the counts feed one footer line,
        // and a wrong footer on a replayed edition beats a NullPointerException.
        String legacy = """
                {
                  "tweets": [], "hackerNewsPosts": [], "githubRepos": [], "rssItems": [],
                  "redditPosts": [], "papers": [], "releases": [], "huggingFaceModels": [],
                  "productHuntPosts": [], "securityAdvisories": [], "jepUpdates": [],
                  "cncfProjectUpdates": [], "radarEntries": [], "conferenceTalks": [],
                  "collectedAt": "2026-05-14T10:00:00",
                  "rawTweetsCount": 214, "rawHackerNewsCount": 118, "rawGithubCount": 60,
                  "rawRssCount": 173, "rawRedditCount": 86
                }
                """;

        ResearchResult restored = mapper.readValue(legacy, ResearchResult.class);

        assertThat(restored.rawCounts()).isEqualTo(RawCounts.NONE);
        assertThat(restored.rawTotalCount())
                .as("the five flat counts are gone, so the total is what the lists still say")
                .isZero();
        assertThat(restored.summary()).contains("Zebrano");
    }

    @Test
    void aFreshlyStoredEditionRoundTripsItsCounts() throws Exception {
        ResearchResult original = new ResearchResult(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.time.LocalDateTime.parse("2026-05-14T10:00:00"),
                RawCounts.builder().tweets(214).hackerNews(118).github(60).rss(173).reddit(86).build(),
                java.util.List.of());

        ResearchResult restored = mapper.readValue(mapper.writeValueAsString(original), ResearchResult.class);

        assertThat(restored.rawCounts()).isEqualTo(original.rawCounts());
        assertThat(restored.rawTotalCount()).isEqualTo(651);
    }
}
