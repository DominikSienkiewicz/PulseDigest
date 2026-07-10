package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendVelocityServiceTest {

    private static final Instant JUL_01 = Instant.parse("2026-07-01T06:00:00Z");
    private static final Instant JUL_03 = Instant.parse("2026-07-03T06:00:00Z");
    private static final Instant JUL_06 = Instant.parse("2026-07-06T06:00:00Z");
    private static final Instant JUL_08 = Instant.parse("2026-07-08T06:00:00Z");

    private final TrendVelocityService service = new TrendVelocityService();

    private static Signal signal(String topicKey, SignalRank rank, int score, SourceDomain... domains) {
        DigestItem item = new DigestItem("Story " + topicKey, "https://example.com/" + topicKey, "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, topicKey);
        return new Signal(item, rank, score, List.of(domains));
    }

    private static PastEdition edition(Instant at, Signal... signals) {
        return new PastEdition(at, List.of(signals));
    }

    @Test
    void aStoryGainingDomainsAndScoreIsFlaggedAsACriticalCandidate() {
        // Two domains and climbing is one independent confirmation away from 🔴.
        List<Signal> annotated = service.annotate(
                List.of(signal("mcp", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)),
                List.of(edition(JUL_06, signal("mcp", SignalRank.MODERATE, 70, SourceDomain.CODE))));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.velocity().criticalCandidate()).isTrue();
            assertThat(s.velocity().domainGrowth()).isEqualTo(1);
            assertThat(s.velocity().scoreGrowth()).isEqualTo(25);
        });
    }

    @Test
    void aStoryStuckOnOneDomainIsNotACandidateHoweverFastItsScoreGrows() {
        // Engagement alone is not confirmation. A single-domain spike is a hype spike.
        List<Signal> annotated = service.annotate(
                List.of(signal("hype", SignalRank.STRONG, 99, SourceDomain.SOCIAL)),
                List.of(edition(JUL_06, signal("hype", SignalRank.WEAK, 40, SourceDomain.SOCIAL))));

        assertThat(annotated).singleElement()
                .satisfies(s -> assertThat(s.velocity().criticalCandidate()).isFalse());
    }

    @Test
    void aStoryThatIsAlreadyCriticalIsNeverACandidate() {
        List<Signal> annotated = service.annotate(
                List.of(signal("mcp", SignalRank.CRITICAL, 130,
                        SourceDomain.CODE, SourceDomain.SCIENCE, SourceDomain.BUSINESS)),
                List.of(edition(JUL_06, signal("mcp", SignalRank.MODERATE, 70, SourceDomain.CODE))));

        assertThat(annotated).singleElement()
                .satisfies(s -> assertThat(s.velocity().criticalCandidate()).isFalse());
    }

    @Test
    void aStoryLosingGroundIsNotACandidate() {
        List<Signal> annotated = service.annotate(
                List.of(signal("fading", SignalRank.MODERATE, 70, SourceDomain.CODE, SourceDomain.SCIENCE)),
                List.of(edition(JUL_06, signal("fading", SignalRank.STRONG, 95,
                        SourceDomain.CODE, SourceDomain.SCIENCE, SourceDomain.BUSINESS))));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.velocity().criticalCandidate()).isFalse();
            assertThat(s.velocity().domainGrowth()).isEqualTo(-1);
        });
    }

    @Test
    void aBrandNewStoryHasNoTrajectoryAndThereforeNoVelocity() {
        List<Signal> annotated = service.annotate(
                List.of(signal("fresh", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)),
                List.of(edition(JUL_06, signal("other", SignalRank.WEAK, 40, SourceDomain.SOCIAL))));

        assertThat(annotated).singleElement().satisfies(s -> assertThat(s.velocity()).isNull());
    }

    @Test
    void theBaselineIsTheOldestEditionInsideTheVelocityWindow() {
        // Window is 3 past editions. JUL_01 falls outside it, so JUL_03 is the baseline.
        List<Signal> annotated = service.annotate(
                List.of(signal("mcp", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)),
                List.of(edition(JUL_08, signal("mcp", SignalRank.MODERATE, 80, SourceDomain.CODE)),
                        edition(JUL_06, signal("mcp", SignalRank.MODERATE, 75, SourceDomain.CODE)),
                        edition(JUL_03, signal("mcp", SignalRank.MODERATE, 70, SourceDomain.CODE)),
                        edition(JUL_01, signal("mcp", SignalRank.WEAK, 40, SourceDomain.SOCIAL))));

        assertThat(annotated).singleElement()
                .satisfies(s -> assertThat(s.velocity().scoreGrowth()).isEqualTo(25));
    }

    // --- public accuracy metric ---

    @Test
    void accuracyCountsACandidateThatLaterReachedCritical() {
        Signal flagged = signal("mcp", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)
                .withVelocity(new TrendVelocity(1, 25, true));
        Signal fulfilled = signal("mcp", SignalRank.CRITICAL, 130,
                SourceDomain.CODE, SourceDomain.SCIENCE, SourceDomain.BUSINESS);

        RadarAccuracy accuracy = service.accuracy(List.of(edition(JUL_06, flagged), edition(JUL_08, fulfilled)));

        assertThat(accuracy.flagged()).isEqualTo(1);
        assertThat(accuracy.confirmed()).isEqualTo(1);
    }

    @Test
    void accuracyCountsACandidateThatNeverMadeItAsAMiss() {
        Signal flagged = signal("hype", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)
                .withVelocity(new TrendVelocity(1, 25, true));
        Signal fizzled = signal("hype", SignalRank.WEAK, 40, SourceDomain.SOCIAL);

        RadarAccuracy accuracy = service.accuracy(List.of(edition(JUL_06, flagged), edition(JUL_08, fizzled)));

        assertThat(accuracy.flagged()).isEqualTo(1);
        assertThat(accuracy.confirmed()).isZero();
    }

    @Test
    void aCandidateWithNoLaterEditionIsStillPendingAndDoesNotCountEitherWay() {
        // Judging a prediction before its verdict window closed would flatter the metric.
        Signal flagged = signal("mcp", SignalRank.STRONG, 95, SourceDomain.CODE, SourceDomain.SCIENCE)
                .withVelocity(new TrendVelocity(1, 25, true));

        assertThat(service.accuracy(List.of(edition(JUL_08, flagged))).flagged()).isZero();
    }

    @Test
    void accuracyIsAbsentUntilTheRadarHasEverPredictedAnything() {
        RadarAccuracy accuracy = service.accuracy(List.of(edition(JUL_06, signal("x", SignalRank.WEAK, 40))));

        assertThat(accuracy.hasVerdict()).isFalse();
    }
}
