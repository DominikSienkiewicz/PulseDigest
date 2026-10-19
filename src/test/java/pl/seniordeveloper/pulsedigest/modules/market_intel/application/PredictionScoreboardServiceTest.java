package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionOutcome;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionScoreboard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** N11. The digest grading its own badge — including the parts it is not allowed to grade yet. */
class PredictionScoreboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    private final PredictionScoreboardService service = new PredictionScoreboardService();

    @Test
    void aCriticalThatKeptComingBackHeldUp() {
        // Same URL in a later edition: the narrowest evidence that the story persisted.
        PredictionScoreboard board = service.measure(List.of(
                edition(60, critical("Loom", "https://example.com/loom")),
                edition(50, strong("Loom", "https://example.com/loom"))),
                Set.of(), NOW, 90, 30);

        assertThat(board.graded()).isEqualTo(1);
        assertThat(board.heldUp()).isEqualTo(1);
        assertThat(board.outcomes()).first()
                .extracting(PredictionOutcome::laterAppearances).isEqualTo(1);
    }

    @Test
    void aCriticalNothingEverCarriedAgainDidNot() {
        PredictionScoreboard board = service.measure(List.of(
                edition(60, critical("Hype", "https://example.com/hype")),
                edition(50, strong("Other", "https://example.com/other"))),
                Set.of(), NOW, 90, 30);

        assertThat(board.heldUp()).isZero();
        assertThat(board.outcomes()).first()
                .extracting(PredictionOutcome::verdict).asString().contains("zgas");
    }

    @Test
    void theReaderSayingItMatteredIsEnoughOnItsOwn() {
        // Two independent kinds of evidence, deliberately not weighted against each other.
        PredictionScoreboard board = service.measure(
                List.of(edition(60, critical("Quiet", "https://example.com/quiet"))),
                Set.of("https://example.com/quiet"), NOW, 90, 30);

        assertThat(board.heldUp()).isEqualTo(1);
    }

    @Test
    void aCallTooYoungToHaveAnOutcomeIsNotGradedAtAll() {
        // Counting it would drag every hit rate towards zero by construction — the same mistake
        // CalibrationService avoids by excluding the edition being assembled.
        PredictionScoreboard board = service.measure(
                List.of(edition(5, critical("Fresh", "https://example.com/fresh"))),
                Set.of(), NOW, 90, 30);

        assertThat(board.graded()).isZero();
    }

    @Test
    void onlyCriticalsAreGraded() {
        PredictionScoreboard board = service.measure(
                List.of(edition(60, strong("Strong", "https://example.com/strong"))),
                Set.of(), NOW, 90, 30);

        assertThat(board.graded()).isZero();
    }

    @Test
    void aRateIsNotReportedUntilThereIsEnoughToReportOne() {
        PredictionScoreboard two = service.measure(List.of(
                edition(60, critical("A", "https://example.com/a")),
                edition(59, critical("B", "https://example.com/b"))),
                Set.of(), NOW, 90, 30);

        assertThat(two.graded()).isEqualTo(2);
        assertThat(two.isReportable())
                .as("below three, one story swings the percentage by a third")
                .isFalse();
    }

    @Test
    void anEmptyHistoryGradesNothingRatherThanClaimingAPerfectRecord() {
        assertThat(service.measure(List.of(), Set.of(), NOW, 90, 30).graded()).isZero();
        assertThat(service.measure(null, null, NOW, 90, 30).hitRatePercent()).isZero();
    }

    private static PastEdition edition(int daysAgo, Signal... signals) {
        return new PastEdition(NOW.minus(Duration.ofDays(daysAgo)), List.of(signals), List.of());
    }

    private static Signal critical(String title, String url) {
        return TestModels.signal(item(title, url), SignalRank.CRITICAL, 160, List.of());
    }

    private static Signal strong(String title, String url) {
        return TestModels.signal(item(title, url), SignalRank.STRONG, 110, List.of());
    }

    private static DigestItem item(String title, String url) {
        return TestModels.digestItem(title, url, "Hacker News", "Java/JVM", "NEWS", 9, null, "s", "w");
    }
}
