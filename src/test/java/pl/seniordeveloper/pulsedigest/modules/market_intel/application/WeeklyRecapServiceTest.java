package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastTopic;
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

    private static final LocalDate FRIDAY = LocalDate.of(2026, 7, 10);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 8);
    private static final Instant MONDAY_RUN = Instant.parse("2026-07-06T06:00:00Z");
    private static final Instant WEDNESDAY_RUN = Instant.parse("2026-07-08T06:00:00Z");

    private final WeeklyRecapService service = new WeeklyRecapService();

    private static Signal signal(String topicKey, SignalRank rank) {
        DigestItem item = new DigestItem("Story " + topicKey, "https://example.com/" + topicKey, "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, topicKey);
        return new Signal(item, rank, 100, List.of(SourceDomain.CODE));
    }

    private static PastEdition edition(Instant at, String topicKey, SignalRank rank) {
        return new PastEdition(at, List.of(
                new PastTopic(topicKey, "Story " + topicKey, "https://example.com/" + topicKey, rank)));
    }

    @Test
    void producesNoRecapOnANonFridayEdition() {
        Optional<WeeklyRecap> recap = service.assemble(WEDNESDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(MONDAY_RUN, "mcp", SignalRank.MODERATE)));

        assertThat(recap).isEmpty();
    }

    @Test
    void reportsAStoryThatClimbedFromModerateToCritical() {
        Optional<WeeklyRecap> recap = service.assemble(FRIDAY,
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
        Optional<WeeklyRecap> recap = service.assemble(FRIDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(MONDAY_RUN, "mcp", SignalRank.CRITICAL)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> assertThat(entry.change()).isEqualTo(RecapChange.CONFIRMED));
    }

    @Test
    void staysSilentAboutStoriesThatNeitherClimbedNorWereCritical() {
        Optional<WeeklyRecap> recap = service.assemble(FRIDAY,
                List.of(signal("quiet", SignalRank.MODERATE)),
                List.of(edition(MONDAY_RUN, "quiet", SignalRank.MODERATE)));

        assertThat(recap).isEmpty();
    }

    @Test
    void aStoryThatFadedFromCriticalIsReportedAsFaded() {
        Optional<WeeklyRecap> recap = service.assemble(FRIDAY,
                List.of(signal("hype", SignalRank.WEAK)),
                List.of(edition(MONDAY_RUN, "hype", SignalRank.CRITICAL)));

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class))
                .singleElement()
                .satisfies(entry -> assertThat(entry.change()).isEqualTo(RecapChange.FADED));
    }

    @Test
    void usesTheEarliestRankOfTheWeekAsTheBaseline() {
        // Monday MODERATE → Wednesday STRONG → Friday CRITICAL is one climb, measured from Monday.
        Optional<WeeklyRecap> recap = service.assemble(FRIDAY,
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(WEDNESDAY_RUN, "mcp", SignalRank.STRONG),
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

        Optional<WeeklyRecap> recap = service.assemble(FRIDAY, signals, history);

        assertThat(recap).get().extracting(WeeklyRecap::entries).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(RecapEntry.class)).hasSize(7);
    }

    @Test
    void producesNoRecapWhenThereIsNoHistoryToCompareAgainst() {
        assertThat(service.assemble(FRIDAY, List.of(signal("mcp", SignalRank.CRITICAL)), List.of())).isEmpty();
    }
}
