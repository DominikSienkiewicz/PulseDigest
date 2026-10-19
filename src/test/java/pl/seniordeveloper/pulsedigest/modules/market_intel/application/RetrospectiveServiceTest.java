package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.domain.TestHistory;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RetrospectivePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.domain.TestFeedback;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** N20. Built from stored editions alone, so the same quarter always produces the same thing. */
class RetrospectiveServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    @Test
    void anEmptyWindowReportsItselfEmptyRatherThanPretending() {
        Retrospective retrospective = service(List.of(), Set.of()).assemble(TODAY);

        assertThat(retrospective.isEmpty()).isTrue();
        assertThat(retrospective.scoreboard().graded()).isZero();
    }

    @Test
    void itCountsTheEditionsItReadAndGradesTheOldCalls() {
        Retrospective retrospective = service(List.of(
                edition(70, critical("Loom", "https://example.com/loom")),
                edition(60, critical("Loom", "https://example.com/loom"))),
                Set.of()).assemble(TODAY);

        assertThat(retrospective.editions()).isEqualTo(2);
        assertThat(retrospective.scoreboard().graded()).isEqualTo(2);
    }

    @Test
    void storiesCarriedMoreThanOnceAreTheOnesThatDidNotGoAway() {
        Retrospective retrospective = service(List.of(
                edition(70, critical("Loom", "https://example.com/loom")),
                edition(60, critical("Loom", "https://example.com/loom")),
                edition(50, critical("One-off", "https://example.com/once"))),
                Set.of()).assemble(TODAY);

        assertThat(retrospective.persistent())
                .as("a story seen once is not persistence")
                .extracting(Retrospective.RecurringStory::appearances).containsExactly(2);
    }

    @Test
    void whatTheReaderEndorsedIsReadFromVotesAndNotRejudged() {
        Retrospective retrospective = service(
                List.of(edition(70, critical("Liked", "https://example.com/liked"))),
                Set.of("https://example.com/liked")).assemble(TODAY);

        assertThat(retrospective.endorsed()).extracting(DigestItem::url)
                .containsExactly("https://example.com/liked");
    }

    private static RetrospectiveService service(List<PastEdition> editions, Set<String> upvoted) {
        return new RetrospectiveService(new TestHistory(editions), new TestFeedback(upvoted),
                new PredictionScoreboardService(), new SourceYieldService(),
                new RetrospectivePolicy(90, 30));
    }

    private static PastEdition edition(int daysAgo, pl.seniordeveloper.pulsedigest.modules.market_intel
            .domain.model.Signal signal) {
        return new PastEdition(NOW.minus(Duration.ofDays(daysAgo)), List.of(signal), List.of());
    }

    private static pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal critical(
            String title, String url) {
        return TestModels.signal(TestModels.digestItem(title, url, "Hacker News", "Java/JVM", "NEWS",
                9, null, "s", "w"), SignalRank.CRITICAL, 160, List.of());
    }
}
