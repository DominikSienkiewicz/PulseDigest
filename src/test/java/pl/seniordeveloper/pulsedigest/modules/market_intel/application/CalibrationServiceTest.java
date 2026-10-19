package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Calibration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalibrationServiceTest {

    private final CalibrationService service = new CalibrationService();

    @Test
    void countsOnlyCriticalStoriesAndOnlyTheOnesTheReaderUpVoted() {
        PastEdition edition = TestModels.pastEdition(Instant.now(), List.of(signal("https://x/1", SignalRank.CRITICAL),
                signal("https://x/2", SignalRank.CRITICAL),
                signal("https://x/3", SignalRank.CRITICAL),
                signal("https://x/4", SignalRank.MODERATE)));

        Calibration c = service.measure(List.of(edition), Set.of("https://x/1", "https://x/4"));

        assertThat(c.criticalItems()).as("the MODERATE story is not part of the badge's claim").isEqualTo(3);
        assertThat(c.upvoted()).as("the up-vote on a MODERATE story does not count for 🔴").isEqualTo(1);
        assertThat(c.percent()).isEqualTo(33);
    }

    /**
     * "100% (1/1)" is a number that looks like calibration and is not. The line stays absent until
     * there is enough to divide by, because a hit rate that overclaims from two data points damages
     * the badge it is meant to vouch for.
     */
    @Test
    void saysNothingUntilThereIsEnoughToDivideBy() {
        PastEdition thin = TestModels.pastEdition(Instant.now(), List.of(signal("https://x/1", SignalRank.CRITICAL),
                signal("https://x/2", SignalRank.CRITICAL)));

        assertThat(service.measure(List.of(thin), Set.of("https://x/1")).isReportable()).isFalse();
    }

    @Test
    void theSameStoryCarriedByTwoEditionsCountsOnce() {
        PastEdition first = TestModels.pastEdition(Instant.now(), List.of(signal("https://x/1", SignalRank.CRITICAL)));
        PastEdition second = TestModels.pastEdition(Instant.now(), List.of(signal("https://x/1", SignalRank.CRITICAL)));

        assertThat(service.measure(List.of(first, second), Set.of()).criticalItems()).isEqualTo(1);
    }

    @Test
    void withNoHistoryOrNoVotesNothingIsClaimed() {
        assertThat(service.measure(List.of(), Set.of("https://x/1")).isReportable()).isFalse();
        assertThat(service.measure(null, Set.of()).criticalItems()).isZero();
        PastEdition edition = TestModels.pastEdition(Instant.now(), List.of(signal("https://x/1", SignalRank.CRITICAL), signal("https://x/2", SignalRank.CRITICAL),
                signal("https://x/3", SignalRank.CRITICAL)));
        assertThat(service.measure(List.of(edition), null).percent())
                .as("no votes yet is 0%, and that is a true statement rather than a missing one")
                .isZero();
    }

    private static Signal signal(String url, SignalRank rank) {
        DigestItem item = TestModels.digestItem("title", url, "GitHub", "Java", "RELEASE", 7, 0, "s", "w");
        return new Signal(item, rank, 120, List.of(), null, null, null);
    }
}
