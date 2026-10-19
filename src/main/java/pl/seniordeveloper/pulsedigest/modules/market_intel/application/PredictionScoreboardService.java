package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionOutcome;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionScoreboard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Grades the 🔴 badge against what actually happened (N11).
 *
 * <p>Every input is stored: the editions that made the calls, the editions that followed, and the
 * reader's votes. Nothing is re-judged at grading time, which is the property that makes the grade
 * worth anything — a scoreboard the graded system can influence is a press release.
 *
 * <p>Only calls old enough to have had an outcome are graded. A story flagged in the most recent
 * edition has had no chance to recur, and counting it would drag every hit rate towards zero by
 * construction — the same mistake {@code CalibrationService} avoids by excluding this edition.
 */
@Slf4j
@Service
public class PredictionScoreboardService {

    public PredictionScoreboard measure(List<PastEdition> editions, Set<String> upvotedUrls,
                                        Instant now, int windowDays, int maturityDays) {
        if (editions == null || editions.isEmpty()) {
            return PredictionScoreboard.empty(windowDays, maturityDays);
        }
        Set<String> upvoted = upvotedUrls != null ? upvotedUrls : Set.of();
        Instant matureBefore = now.minus(Duration.ofDays(maturityDays));
        List<PastEdition> ordered = editions.stream()
                .filter(edition -> edition.generatedAt() != null)
                .sorted(Comparator.comparing(PastEdition::generatedAt))
                .toList();

        List<PredictionOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            PastEdition edition = ordered.get(i);
            if (!edition.generatedAt().isBefore(matureBefore)) {
                continue;
            }
            List<PastEdition> later = ordered.subList(i + 1, ordered.size());
            for (Signal signal : edition.signals()) {
                if (signal.rank() != SignalRank.CRITICAL) {
                    continue;
                }
                String url = signal.item().url();
                if (url == null || url.isBlank()) {
                    continue;
                }
                outcomes.add(new PredictionOutcome(signal.item().title(), url, edition.generatedAt(),
                        countLaterAppearances(later, signal), upvoted.contains(url)));
            }
        }

        List<PredictionOutcome> newestFirst = outcomes.stream()
                .sorted(Comparator.comparing(PredictionOutcome::flaggedAt).reversed())
                .toList();
        PredictionScoreboard scoreboard = new PredictionScoreboard(newestFirst, windowDays, maturityDays);
        log.info("prediction-scoreboard graded={} held_up={} hit_rate={}% window_days={} maturity_days={}",
                scoreboard.graded(), scoreboard.heldUp(), scoreboard.hitRatePercent(),
                windowDays, maturityDays);
        return scoreboard;
    }

    /**
     * How many later editions carried the same story.
     *
     * <p>Matched on the URL, and on the topic key only when the item actually has one.
     * {@code correlationKey()} falls back to the <em>category</em> for items the model gave no topic
     * key, which is right for the clustering it was written for and badly wrong here: almost every
     * edition carries something in "Java/JVM", so a scoreboard matching on it would count unrelated
     * items as the same story and report a hit rate close to 100% regardless of the truth.
     *
     * <p>A grading routine that flatters the thing it grades is worse than none, so this matches
     * narrowly and under-counts rather than over-counts.
     */
    private static int countLaterAppearances(List<PastEdition> later, Signal signal) {
        String url = signal.item().url();
        String topicKey = signal.item().topicKey();
        boolean hasTopicKey = topicKey != null && !topicKey.isBlank();
        return (int) later.stream()
                .filter(edition -> edition.signals().stream().anyMatch(other ->
                        url.equals(other.item().url())
                                || (hasTopicKey && topicKey.equalsIgnoreCase(other.item().topicKey()))))
                .count();
    }
}
