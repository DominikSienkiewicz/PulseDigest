package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CategoryPreference;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceWeights;

import java.util.Comparator;
import java.util.HashMap;
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
        return score(items, netVotesBySource, Map.of());
    }

    /**
     * Scores with per-category reader preference applied on top of per-source nudging (C13).
     *
     * @param netVotesByCategory lower-cased category → net (UP − DOWN) votes; empty degrades to C6
     */
    public List<Signal> score(List<DigestItem> items, Map<String, Integer> netVotesBySource,
                              Map<String, Integer> netVotesByCategory) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(netVotesBySource, "netVotesBySource must not be null");
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, Set<SourceDomain>> domainsByTopic = buildDomainMap(items);
        Map<String, Integer> netVotesByWeightKey = aggregateByWeightKey(netVotesBySource);
        Map<String, Integer> categoryVotes = netVotesByCategory != null ? netVotesByCategory : Map.of();
        List<Signal> signals = items.stream()
                .map(item -> scoreItem(item, domainsByTopic, netVotesByWeightKey, categoryVotes))
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

    /**
     * Maps each story (by {@link DigestItem#correlationKey()}) to the set of source domains that
     * carried it. Grouping by story rather than by category is what makes the 3-domain rule mean
     * "independently confirmed" instead of "the umbrella category AI/LLM had a busy day".
     */
    private Map<String, Set<SourceDomain>> buildDomainMap(List<DigestItem> items) {
        return items.stream()
                .filter(i -> !i.correlationKey().isEmpty())
                .collect(Collectors.groupingBy(
                        DigestItem::correlationKey,
                        Collectors.mapping(i -> SourceDomain.from(i.source()), Collectors.toSet())
                ));
    }

    /**
     * Collapses raw per-label feedback counts (e.g. {@code arXiv/cs.AI}, {@code arXiv/cs.LG}) into
     * base-source buckets ({@code arXiv}), so votes aggregate at the same granularity as the weight
     * they nudge instead of scattering across high-cardinality labels.
     */
    private static Map<String, Integer> aggregateByWeightKey(Map<String, Integer> rawNetVotes) {
        Map<String, Integer> byKey = new HashMap<>();
        rawNetVotes.forEach((source, votes) -> byKey.merge(SourceWeights.keyOf(source), votes, Integer::sum));
        return byKey;
    }

    private Signal scoreItem(DigestItem item, Map<String, Set<SourceDomain>> domainsByTopic,
                             Map<String, Integer> netVotesByWeightKey,
                             Map<String, Integer> netVotesByCategory) {
        int netVotes = netVotesByWeightKey.getOrDefault(SourceWeights.keyOf(item.source()), 0);
        double weight = SourceWeights.of(item.source(), netVotes);
        int baseScore = (int) Math.round(weight * 100);
        int engagement = item.engagementScore() != null ? item.engagementScore() : 0;
        int engagementBonus = Math.min(MAX_ENGAGEMENT_BONUS, engagement / ENGAGEMENT_DIVISOR);

        Set<SourceDomain> domains = domainsByTopic.getOrDefault(item.correlationKey(), Set.of());
        int crossSourceBonus = domains.size() >= CROSS_SOURCE_THRESHOLD ? CROSS_SOURCE_BONUS : 0;

        // Reader taste scales credibility and engagement — never corroboration. A muted category
        // that three independent domains confirm still reaches CRITICAL, which is what keeps the
        // preference loop from becoming a trap the category can never climb out of.
        double preference = CategoryPreference.multiplierFor(item.category(), netVotesByCategory);
        int signalScore = (int) Math.round((baseScore + engagementBonus) * preference) + crossSourceBonus;
        SignalRank rank = toRank(signalScore);

        List<SourceDomain> sortedDomains = domains.stream()
                .sorted(Comparator.comparing(SourceDomain::name))
                .toList();

        // Keep every component instead of discarding it: the digest owes the reader an explanation of
        // why an item surfaced, and of what his own votes did to it.
        ScoreBreakdown breakdown = new ScoreBreakdown(
                SourceWeights.keyOf(item.source()), SourceWeights.of(item.source()), weight, netVotes,
                engagementBonus, preference,
                CategoryPreference.netVotesFor(item.category(), netVotesByCategory), crossSourceBonus);

        return new Signal(item, rank, signalScore, sortedDomains, null, null, breakdown);
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
