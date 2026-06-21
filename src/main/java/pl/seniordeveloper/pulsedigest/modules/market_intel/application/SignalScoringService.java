package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceWeights;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic signal scorer — assigns SignalRank to each DigestItem based on
 * source credibility weight, engagement, and cross-source correlation.
 * No LLM calls; purely deterministic post-synthesis logic.
 */
@Slf4j
@Service
public class SignalScoringService {

    private static final int CROSS_SOURCE_THRESHOLD = 3;
    private static final int CROSS_SOURCE_BONUS = 50;
    private static final int MAX_ENGAGEMENT_BONUS = 50;
    private static final int ENGAGEMENT_DIVISOR = 1_000;
    // Score of exactly 100 is STRONG; reaching it now needs an engagement or cross-source bonus on top
    // of a high base weight (no single source weight hits 100 alone), and 101+ is CRITICAL.
    private static final int CRITICAL_THRESHOLD = 101;
    private static final int STRONG_THRESHOLD = 100;
    private static final int MODERATE_THRESHOLD = 60;

    /** Scores all items with no feedback nudging — see {@link #score(List, Map)}. */
    public List<Signal> score(List<DigestItem> items) {
        return score(items, Map.of());
    }

    /**
     * Scores all items and returns them sorted CRITICAL → STRONG → MODERATE → WEAK, score desc within
     * rank. {@code netVotesBySource} (C6) nudges each item's source-credibility weight by accumulated
     * reader feedback (UP − DOWN); an empty map means no nudging.
     */
    public List<Signal> score(List<DigestItem> items, Map<String, Integer> netVotesBySource) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(netVotesBySource, "netVotesBySource must not be null");
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, Set<SourceDomain>> domainsByCategory = buildDomainMap(items);
        List<Signal> signals = items.stream()
                .map(item -> scoreItem(item, domainsByCategory, netVotesBySource))
                .sorted(byRankThenScoreDesc())
                .toList();
        log.debug("Scored {} items: {} CRITICAL, {} STRONG, {} MODERATE, {} WEAK",
                signals.size(),
                countByRank(signals, SignalRank.CRITICAL),
                countByRank(signals, SignalRank.STRONG),
                countByRank(signals, SignalRank.MODERATE),
                countByRank(signals, SignalRank.WEAK));
        return signals;
    }

    private Map<String, Set<SourceDomain>> buildDomainMap(List<DigestItem> items) {
        return items.stream()
                .filter(i -> i.category() != null && !i.category().isBlank())
                .collect(Collectors.groupingBy(
                        i -> i.category().toLowerCase(),
                        Collectors.mapping(i -> SourceDomain.from(i.source()), Collectors.toSet())
                ));
    }

    private Signal scoreItem(DigestItem item, Map<String, Set<SourceDomain>> domainsByCategory,
                             Map<String, Integer> netVotesBySource) {
        int netVotes = netVotesBySource.getOrDefault(item.source(), 0);
        double weight = SourceWeights.of(item.source(), netVotes);
        int baseScore = (int) Math.round(weight * 100);
        int engagement = item.engagementScore() != null ? item.engagementScore() : 0;
        int engagementBonus = Math.min(MAX_ENGAGEMENT_BONUS, engagement / ENGAGEMENT_DIVISOR);

        String categoryKey = item.category() != null ? item.category().toLowerCase() : "";
        Set<SourceDomain> domains = domainsByCategory.getOrDefault(categoryKey, Set.of());
        int crossSourceBonus = domains.size() >= CROSS_SOURCE_THRESHOLD ? CROSS_SOURCE_BONUS : 0;

        int signalScore = baseScore + engagementBonus + crossSourceBonus;
        SignalRank rank = toRank(signalScore);

        List<SourceDomain> sortedDomains = domains.stream()
                .sorted(Comparator.comparing(SourceDomain::name))
                .toList();

        return new Signal(item, rank, signalScore, sortedDomains);
    }

    private static SignalRank toRank(int score) {
        if (score >= CRITICAL_THRESHOLD) {
            return SignalRank.CRITICAL;
        }
        if (score >= STRONG_THRESHOLD) {
            return SignalRank.STRONG;
        }
        if (score >= MODERATE_THRESHOLD) {
            return SignalRank.MODERATE;
        }
        return SignalRank.WEAK;
    }

    private static Comparator<Signal> byRankThenScoreDesc() {
        return Comparator
                .comparingInt((Signal s) -> s.rank().ordinal())
                .thenComparing((a, b) -> Integer.compare(b.signalScore(), a.signalScore()));
    }

    private static long countByRank(List<Signal> signals, SignalRank rank) {
        return signals.stream().filter(s -> s.rank() == rank).count();
    }
}
