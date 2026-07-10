package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns a point into a trajectory: annotates each signal with how many consecutive editions have
 * carried the same story, and when the reader first saw it.
 *
 * <p>Pure and framework-free. The streak counts the current edition, so a story published for the
 * first time has a streak of 1. A gap resets the streak but not {@code firstSeenAt} — "this has been
 * building for three editions" and "you first read about this on 18.06" answer different questions.
 */
public final class TrendMemory {

    private TrendMemory() {
    }

    /**
     * Returns the signals with recurrence attached. Signals whose item has no correlation key are
     * returned unchanged (recurrence stays null) — there is nothing to match them on across editions.
     *
     * @param signals  the signals of the edition being assembled
     * @param editions previously published editions, in any order
     */
    public static List<Signal> annotate(List<Signal> signals, List<PastEdition> editions) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        List<PastEdition> newestFirst = editions == null ? List.of() : editions.stream()
                .sorted(Comparator.comparing(PastEdition::generatedAt).reversed())
                .toList();

        return signals.stream()
                .map(signal -> annotateOne(signal, newestFirst))
                .toList();
    }

    private static Signal annotateOne(Signal signal, List<PastEdition> newestFirst) {
        String topicKey = signal.item().correlationKey();
        if (topicKey.isEmpty()) {
            return signal;
        }
        return signal.withRecurrence(new TrendRecurrence(
                1 + consecutiveEditionsCarrying(topicKey, newestFirst),
                firstSighting(topicKey, newestFirst).orElse(null)));
    }

    /** Editions carrying the topic in an unbroken run back from the most recent one. */
    private static int consecutiveEditionsCarrying(String topicKey, List<PastEdition> newestFirst) {
        int streak = 0;
        for (PastEdition edition : newestFirst) {
            if (!edition.carries(topicKey)) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private static Optional<LocalDate> firstSighting(String topicKey, List<PastEdition> editions) {
        return editions.stream()
                .filter(edition -> edition.carries(topicKey))
                .map(PastEdition::generatedAt)
                .min(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate());
    }
}
