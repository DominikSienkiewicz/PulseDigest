package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Predicts which stories are about to become Critical Trends, deterministically, from the digest's
 * own history. 🔴 tells the reader a trend has already broken; the architect's edge is being there
 * before that.
 *
 * <p>A story is flagged when it is <em>gathering independent confirmation</em>: it has picked up at
 * least one new source domain inside the window, its deterministic score is rising, and it already
 * stands on two domains — one confirmation short of the three that make it CRITICAL. A single-domain
 * score spike is explicitly not a candidate: that is engagement, not corroboration.
 *
 * <p>Predictions are stored on the signal, so a later edition can grade them. {@link #accuracy} is
 * that grade, and it is printed in the mail.
 */
@Slf4j
@Service
public class TrendVelocityService {

    /** Editions of history the trajectory is measured over. At Mon/Wed/Fri this is about a week. */
    private static final int VELOCITY_WINDOW_EDITIONS = 3;
    /** Cross-source promotion needs three domains, so two-and-climbing is the interesting state. */
    private static final int CANDIDATE_MIN_DOMAINS = 2;
    /** Enough score movement to be a trend rather than rounding on the engagement bonus. */
    private static final int CANDIDATE_MIN_SCORE_GROWTH = 5;

    /** Returns the signals with velocity attached; those with no trajectory keep a null velocity. */
    public List<Signal> annotate(List<Signal> signals, List<PastEdition> history) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        List<PastEdition> window = newestFirst(history).stream()
                .limit(VELOCITY_WINDOW_EDITIONS)
                .toList();
        if (window.isEmpty()) {
            return signals;
        }
        List<Signal> annotated = signals.stream().map(signal -> annotateOne(signal, window)).toList();
        long candidates = annotated.stream().filter(Signal::isCriticalCandidate).count();
        if (candidates > 0) {
            log.info("Predictive radar: {} critical candidate(s) over a {}-edition window",
                    candidates, window.size());
        }
        return annotated;
    }

    private static Signal annotateOne(Signal signal, List<PastEdition> window) {
        Optional<Signal> baseline = baselineInWindow(signal.item().correlationKey(), window);
        if (baseline.isEmpty()) {
            return signal;
        }
        int domainGrowth = signal.sourceDomains().size() - baseline.get().sourceDomains().size();
        int scoreGrowth = signal.signalScore() - baseline.get().signalScore();
        return signal.withVelocity(new TrendVelocity(domainGrowth, scoreGrowth,
                isCandidate(signal, domainGrowth, scoreGrowth)));
    }

    private static boolean isCandidate(Signal signal, int domainGrowth, int scoreGrowth) {
        return signal.rank() != SignalRank.CRITICAL
                && signal.sourceDomains().size() >= CANDIDATE_MIN_DOMAINS
                && domainGrowth >= 1
                && scoreGrowth >= CANDIDATE_MIN_SCORE_GROWTH;
    }

    /** The story as it stood in the oldest edition of the window that carried it. */
    private static Optional<Signal> baselineInWindow(String topicKey, List<PastEdition> window) {
        if (topicKey.isEmpty()) {
            return Optional.empty();
        }
        return window.stream()
                .filter(edition -> edition.carries(topicKey))
                .min(Comparator.comparing(PastEdition::generatedAt))
                .flatMap(edition -> edition.find(topicKey));
    }

    /**
     * Grades every prediction whose verdict window has closed: a story flagged in some edition counts
     * as confirmed when a <em>later</em> edition ranked it CRITICAL. A candidate flagged in the most
     * recent edition is still pending and is excluded from both numerator and denominator — judging
     * it now would flatter the number.
     */
    public RadarAccuracy accuracy(List<PastEdition> history) {
        List<PastEdition> oldestFirst = newestFirst(history).reversed();
        int flagged = 0;
        int confirmed = 0;
        for (int i = 0; i < oldestFirst.size(); i++) {
            PastEdition edition = oldestFirst.get(i);
            List<PastEdition> later = oldestFirst.subList(i + 1, oldestFirst.size());
            if (later.isEmpty()) {
                break;
            }
            for (Signal signal : edition.signals()) {
                if (!signal.isCriticalCandidate()) {
                    continue;
                }
                flagged++;
                if (reachedCritical(signal.item().correlationKey(), later)) {
                    confirmed++;
                }
            }
        }
        return new RadarAccuracy(flagged, confirmed);
    }

    private static boolean reachedCritical(String topicKey, List<PastEdition> later) {
        return later.stream()
                .map(edition -> edition.find(topicKey))
                .flatMap(Optional::stream)
                .anyMatch(Signal::isCriticalTrend);
    }

    private static List<PastEdition> newestFirst(List<PastEdition> history) {
        return history == null ? List.of() : history.stream()
                .sorted(Comparator.comparing(PastEdition::generatedAt).reversed())
                .toList();
    }
}
