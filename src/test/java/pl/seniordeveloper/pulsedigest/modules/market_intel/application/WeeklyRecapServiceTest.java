package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapChange;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WeeklyRecap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyRecapServiceTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 7, 9);
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 6);
    private static final Instant MONDAY_RUN = Instant.parse("2026-07-06T04:00:00Z");
    /** An ad-hoc {@code workflow_dispatch} run — the only way a third edition lands inside one week. */
    private static final Instant TUESDAY_RUN = Instant.parse("2026-07-07T09:00:00Z");

    private final WeeklyRecapService service = new WeeklyRecapService();

    private static Signal signal(String topicKey, SignalRank rank) {
        DigestItem item = new DigestItem("Story " + topicKey, "https://example.com/" + topicKey, "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, topicKey);
        return new Signal(item, rank, 100, List.of(SourceDomain.CODE));
    }

    private static PastEdition edition(Instant at, String topicKey, SignalRank rank) {
        return new PastEdition(at, List.of(signal(topicKey, rank)));
    }

    @Test
    void producesNoRecapOnANonThursdayEdition() {
        Optional<WeeklyRecap> recap = service.assemble(MONDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(MONDAY_RUN, "mcp", SignalRank.MODERATE)));

        assertThat(recap).isEmpty();
    }

    @Test
    void reportsAStoryThatClimbedFromModerateToCritical() {
        Optional<WeeklyRecap> recap = service.assemble(THURSDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(MONDAY_RUN, "mcp", SignalRank.MODERATE)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.change()).isEqualTo(RecapChange.ESCALATED);
                    assertThat(entry.previousRank()).isEqualTo(SignalRank.MODERATE);
                    assertThat(entry.currentRank()).isEqualTo(SignalRank.CRITICAL);
                });
    }

    @Test
    void reportsAnEarlierCriticalThatHeldItsRank() {
        Optional<WeeklyRecap> recap = service.assemble(THURSDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(MONDAY_RUN, "mcp", SignalRank.CRITICAL)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> assertThat(entry.change()).isEqualTo(RecapChange.CONFIRMED));
    }

    @Test
    void staysSilentAboutStoriesThatNeitherClimbedNorWereCritical() {
        Optional<WeeklyRecap> recap = service.assemble(THURSDAY,
                List.of(signal("quiet", SignalRank.MODERATE)),
                List.of(edition(MONDAY_RUN, "quiet", SignalRank.MODERATE)));

        assertThat(recap).isEmpty();
    }

    @Test
    void aStoryThatFadedFromCriticalIsReportedAsFaded() {
        Optional<WeeklyRecap> recap = service.assemble(THURSDAY,
                List.of(signal("hype", SignalRank.WEAK)),
                List.of(edition(MONDAY_RUN, "hype", SignalRank.CRITICAL)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> assertThat(entry.change()).isEqualTo(RecapChange.FADED));
    }

    @Test
    void usesTheEarliestRankOfTheWeekAsTheBaseline() {
        // Monday MODERATE → ad-hoc Tuesday STRONG → Thursday CRITICAL is one climb, measured from Monday.
        Optional<WeeklyRecap> recap = service.assemble(THURSDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(TUESDAY_RUN, "mcp", SignalRank.STRONG),
                        edition(MONDAY_RUN, "mcp", SignalRank.MODERATE)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> assertThat(entry.previousRank()).isEqualTo(SignalRank.MODERATE));
    }

    @Test
    void capsTheRecapAtSevenLinesSoTheMailDoesNotBloat() {
        List<Signal> signals = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> signal("topic-" + i, SignalRank.CRITICAL))
                .toList();
        List<PastEdition> history = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> edition(MONDAY_RUN, "topic-" + i, SignalRank.MODERATE))
                .toList();

        Optional<WeeklyRecap> recap = service.assemble(THURSDAY, signals, history);

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class)).hasSize(7);
    }

    /**
     * The guard returns empty rather than throwing, and that is a contract, not an accident: the
     * recap is a decoration on the edition, so a missing input must never take the whole report
     * down with it. Without this test the branch is only reachable by deleting the guard, which is
     * exactly the change it exists to catch — the failure would surface as a NullPointerException
     * inside an already-assembled report.
     */
    @Test
    void producesNoRecapWhenTheSignalListIsMissingEntirely() {
        assertThat(service.assemble(THURSDAY, null, List.of(edition(MONDAY_RUN, "mcp", SignalRank.MODERATE))))
                .isEmpty();
    }

    /** Same contract as above on the other argument: absent history is not an error, it is silence. */
    @Test
    void producesNoRecapWhenTheHistoryIsMissingEntirely() {
        assertThat(service.assemble(THURSDAY, List.of(signal("mcp", SignalRank.CRITICAL)), null)).isEmpty();
    }

    @Test
    void producesNoRecapWhenThereIsNoHistoryToCompareAgainst() {
        assertThat(service.assemble(THURSDAY, List.of(signal("mcp", SignalRank.CRITICAL)), List.of())).isEmpty();
    }
}
