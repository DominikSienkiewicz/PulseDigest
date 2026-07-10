package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendMemoryTest {

    private static final Instant JUN_18 = Instant.parse("2026-06-18T06:00:00Z");
    private static final Instant JUN_20 = Instant.parse("2026-06-20T06:00:00Z");
    private static final Instant JUN_22 = Instant.parse("2026-06-22T06:00:00Z");

    private static Signal signal(String topicKey, SignalRank rank) {
        DigestItem item = new DigestItem("Title", "https://example.com/" + topicKey, "GitHub",
                "AI/LLM", "RELEASE", 8, 10, "Summary", null, topicKey);
        return new Signal(item, rank, 100, List.of(SourceDomain.CODE));
    }

    private static PastEdition edition(Instant at, String... topicKeys) {
        return new PastEdition(at, List.of(topicKeys).stream()
                .map(k -> signal(k, SignalRank.MODERATE))
                .toList());
    }

    @Test
    void aTopicSeenInTheTwoPreviousEditionsIsTheThirdInARow() {
        List<Signal> annotated = TrendMemory.annotate(
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(JUN_22, "mcp"), edition(JUN_20, "mcp"), edition(JUN_18, "other")));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.recurrence().editionStreak()).isEqualTo(3);
            assertThat(s.recurrence().firstSeenAt()).isEqualTo(LocalDate.of(2026, 6, 20));
        });
    }

    @Test
    void aStreakBreaksWhenAnEditionInBetweenOmittedTheTopic() {
        // Present, absent, present → the run of consecutive editions ending now is just this one.
        List<Signal> annotated = TrendMemory.annotate(
                List.of(signal("mcp", SignalRank.CRITICAL)),
                List.of(edition(JUN_22, "other"), edition(JUN_20, "mcp"), edition(JUN_18, "mcp")));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.recurrence().editionStreak()).isEqualTo(1);
            // First signal still reaches back past the gap — that is when the reader first saw it.
            assertThat(s.recurrence().firstSeenAt()).isEqualTo(LocalDate.of(2026, 6, 18));
        });
    }

    @Test
    void aBrandNewTopicHasAStreakOfOneAndNoEarlierSighting() {
        List<Signal> annotated = TrendMemory.annotate(
                List.of(signal("gemini-3", SignalRank.STRONG)),
                List.of(edition(JUN_22, "mcp")));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.recurrence().editionStreak()).isEqualTo(1);
            assertThat(s.recurrence().firstSeenAt()).isNull();
        });
    }

    @Test
    void emptyHistoryLeavesEverySignalAtStreakOne() {
        List<Signal> annotated = TrendMemory.annotate(List.of(signal("mcp", SignalRank.CRITICAL)), List.of());

        assertThat(annotated).singleElement()
                .satisfies(s -> assertThat(s.recurrence().editionStreak()).isEqualTo(1));
    }

    @Test
    void signalsWithoutACorrelationKeyAreLeftUnannotated() {
        DigestItem item = new DigestItem("Title", "https://example.com/x", "GitHub",
                null, "RELEASE", 8, 10, "Summary", null, null);
        Signal blank = new Signal(item, SignalRank.WEAK, 40, List.of(SourceDomain.CODE));

        List<Signal> annotated = TrendMemory.annotate(List.of(blank), List.of(edition(JUN_22, "mcp")));

        assertThat(annotated).singleElement().satisfies(s -> assertThat(s.recurrence()).isNull());
    }

    @Test
    void annotationPreservesRankScoreAndDomains() {
        List<Signal> annotated = TrendMemory.annotate(
                List.of(signal("mcp", SignalRank.CRITICAL)), List.of(edition(JUN_22, "mcp")));

        assertThat(annotated).singleElement().satisfies(s -> {
            assertThat(s.rank()).isEqualTo(SignalRank.CRITICAL);
            assertThat(s.signalScore()).isEqualTo(100);
            assertThat(s.sourceDomains()).containsExactly(SourceDomain.CODE);
        });
    }
}
