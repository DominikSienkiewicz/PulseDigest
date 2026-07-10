package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastTopic;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceYield;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SourceYieldServiceTest {

    private static final Instant RUN = Instant.parse("2026-07-06T06:00:00Z");

    private final SourceYieldService service = new SourceYieldService();

    private static PastTopic topic(String source, SignalRank rank) {
        return new PastTopic("t-" + source + rank, "Title", "https://example.com/" + source, rank, source);
    }

    @Test
    void yieldIsTheShareOfASourcesItemsThatEarnedAHighRank() {
        PastEdition edition = new PastEdition(RUN, List.of(
                topic("Reddit", SignalRank.WEAK),
                topic("Reddit", SignalRank.MODERATE),
                topic("GitHub Releases", SignalRank.CRITICAL),
                topic("GitHub Releases", SignalRank.STRONG)));

        List<SourceYield> yields = service.scoreboard(List.of(edition));

        assertThat(yields).anySatisfy(y -> {
            assertThat(y.source()).isEqualTo("GitHub Releases");
            assertThat(y.appearances()).isEqualTo(2);
            assertThat(y.highRankAppearances()).isEqualTo(2);
            assertThat(y.yieldRatio()).isCloseTo(1.0, within(0.001));
        });
        assertThat(yields).anySatisfy(y -> {
            assertThat(y.source()).isEqualTo("Reddit");
            assertThat(y.yieldRatio()).isCloseTo(0.0, within(0.001));
        });
    }

    @Test
    void sourcesAreRankedByYieldSoTheScoreboardReadsTopDown() {
        PastEdition edition = new PastEdition(RUN, List.of(
                topic("Reddit", SignalRank.WEAK),
                topic("Hacker News", SignalRank.CRITICAL)));

        assertThat(service.scoreboard(List.of(edition)))
                .extracting(SourceYield::source)
                .containsExactly("Hacker News", "Reddit");
    }

    @Test
    void labelsAreCollapsedToTheirBaseSourceSoArxivCategoriesDoNotFragmentTheLedger() {
        PastEdition edition = new PastEdition(RUN, List.of(
                topic("arXiv/cs.AI", SignalRank.CRITICAL),
                topic("arXiv/cs.LG", SignalRank.WEAK)));

        assertThat(service.scoreboard(List.of(edition)))
                .singleElement()
                .satisfies(y -> {
                    assertThat(y.source()).isEqualTo("arXiv");
                    assertThat(y.appearances()).isEqualTo(2);
                    assertThat(y.highRankAppearances()).isEqualTo(1);
                });
    }

    @Test
    void anEmptyHistoryProducesAnEmptyScoreboard() {
        assertThat(service.scoreboard(List.of())).isEmpty();
    }

    @Test
    void topicsWithoutASourceAreIgnoredRatherThanBucketedUnderTheEmptyString() {
        PastEdition edition = new PastEdition(RUN, List.of(
                new PastTopic("t", "Title", "https://example.com/x", SignalRank.CRITICAL, null)));

        assertThat(service.scoreboard(List.of(edition))).isEmpty();
    }
}
