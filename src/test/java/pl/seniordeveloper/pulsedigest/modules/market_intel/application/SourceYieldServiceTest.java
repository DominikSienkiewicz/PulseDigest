package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceIntake;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceYield;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SourceYieldServiceTest {

    private static final Instant RUN = Instant.parse("2026-07-06T06:00:00Z");

    private final SourceYieldService service = new SourceYieldService();

    private static Signal topic(String source, SignalRank rank) {
        DigestItem item = new DigestItem("Title", "https://example.com/" + source + rank, source,
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, "t-" + source + rank);
        return TestModels.signal(item, rank, 100, List.of(SourceDomain.CODE));
    }

    @Test
    void yieldIsTheShareOfASourcesItemsThatEarnedAHighRank() {
        PastEdition edition = TestModels.pastEdition(RUN, List.of(
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
        PastEdition edition = TestModels.pastEdition(RUN, List.of(
                topic("Reddit", SignalRank.WEAK),
                topic("Hacker News", SignalRank.CRITICAL)));

        assertThat(service.scoreboard(List.of(edition)))
                .extracting(SourceYield::source)
                .containsExactly("Hacker News", "Reddit");
    }

    @Test
    void labelsAreCollapsedToTheirBaseSourceSoArxivCategoriesDoNotFragmentTheLedger() {
        PastEdition edition = TestModels.pastEdition(RUN, List.of(
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
        DigestItem sourceless = new DigestItem("Title", "https://example.com/x", null,
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, "t");
        PastEdition edition = TestModels.pastEdition(RUN,
                List.of(TestModels.signal(sourceless, SignalRank.CRITICAL, 100, List.of(SourceDomain.CODE))));

        assertThat(service.scoreboard(List.of(edition))).isEmpty();
    }

    /**
     * F7. The yield ledger tallies only sources that appear in an edition's signals, so a source
     * returning an empty result set has no row at all — it reads exactly like a source nobody
     * configured. The reports needed to tell those apart were already being stored per edition and
     * simply never read back.
     *
     * <p>Red before the fix: the intake ledger was empty, so a source fetched in three editions and
     * silent in all three produced no row and no warning.
     */
    @org.junit.jupiter.api.Test
    void aSourceThatKeepsReturningNothingStillGetsALedgerRow() {
        List<PastEdition> history = List.of(editionFetching("Tech Radar", 0, "Hacker News", 12),
                editionFetching("Tech Radar", 0, "Hacker News", 9),
                editionFetching("Tech Radar", 0, "Hacker News", 14));

        List<SourceIntake> ledger = service.intakeLedger(history);

        assertThat(ledger).extracting(SourceIntake::source)
                .as("a silent source must be present, not absent")
                .contains("Tech Radar", "Hacker News");
        assertThat(ledger).filteredOn(i -> i.source().equals("Tech Radar"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.editions()).isEqualTo(3);
                    assertThat(i.silentEditions()).isEqualTo(3);
                    assertThat(i.itemsFetched()).isZero();
                    assertThat(i.isSilent()).isTrue();
                });
    }

    @org.junit.jupiter.api.Test
    void aSourceSilentAcrossTheWholeWindowIsReportedOnceItHasEnoughEditionsToJudge() {
        List<PastEdition> history = List.of(editionFetching("Tech Radar", 0, "Hacker News", 12),
                editionFetching("Tech Radar", 0, "Hacker News", 9),
                editionFetching("Tech Radar", 0, "Hacker News", 14));

        assertThat(service.silentSources(history, 3)).extracting(SourceIntake::source)
                .containsExactly("Tech Radar");
        assertThat(service.silentSources(history, 4))
                .as("three editions is not yet evidence when four are required")
                .isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aSourceThatReturnedSomethingEvenOnceIsNotSilent() {
        List<PastEdition> history = List.of(editionFetching("Tech Radar", 0, "Hacker News", 12),
                editionFetching("Tech Radar", 7, "Hacker News", 9));

        assertThat(service.silentSources(history, 2)).isEmpty();
    }

    /** An edition carrying only fetch reports — the published side is irrelevant to intake. */
    private static PastEdition editionFetching(String firstSource, int firstCount,
                                               String secondSource, int secondCount) {
        return new PastEdition(RUN, List.of(), List.of(SourceFetchReport.success(firstSource, firstCount, 10L),
                SourceFetchReport.success(secondSource, secondCount, 10L)));
    }
}
