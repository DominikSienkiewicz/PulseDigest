package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CategoryPreference;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
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
 * Deterministic signal scorer — assigns a {@link SignalRank} to each {@link DigestItem} from source
 * credibility weight, engagement, and cross-source correlation. No LLM calls.
 *
 * <p><b>CRITICAL means corroboration, not magnitude.</b> A story carried by
 * {@value #CROSS_SOURCE_THRESHOLD} or more distinct source domains is CRITICAL whatever it scores;
 * nothing else ever is, however popular. Until 2026-10-18 rank was a pure function of score, so a
 * single popular source could earn 🔴 while a story genuinely confirmed by three weak sources could
 * not — the opposite of what the README, {@code docs/how-it-works.md} and the predictive radar all
 * describe (F2). Score still orders items *within* a rank, which is what the cross-source bonus is
 * for now that it no longer decides the badge.
 */
@Slf4j
@Service
public class SignalScoringService {

    private final ScoringPolicy policy;

    public SignalScoringService(ScoringPolicy policy) {
        this.policy = policy != null ? policy : ScoringPolicy.defaults();
    }

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
                        Collectors.mapping(i -> policy.domainOf(i.source()), Collectors.toSet())
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
        int engagementBonus = Math.min(policy.maxEngagementBonus(), engagement / policy.engagementDivisor());

        Set<SourceDomain> domains = domainsByTopic.getOrDefault(item.correlationKey(), Set.of());
        boolean crossSourceConfirmed = policy.isCrossSourceConfirmed(domains.size());
        int crossSourceBonus = crossSourceConfirmed ? policy.crossSourceBonus() : 0;

        // Reader taste scales credibility and engagement — never corroboration. A muted category
        // that three independent domains confirm still reaches CRITICAL, which is what keeps the
        // preference loop from becoming a trap the category can never climb out of.
        double preference = CategoryPreference.multiplierFor(item.category(), netVotesByCategory);
        // An explicit mute (N13) scales the same components taste scales, and for the same reason it
        // cannot touch corroboration: rank comes from the domain count, so a muted topic three
        // domains confirm still reaches CRITICAL.
        double mute = policy.isMuted(item.category(), item.topicKey()) ? policy.muteMultiplier() : 1.0;
        int signalScore = (int) Math.round((baseScore + engagementBonus) * preference * mute)
                + crossSourceBonus;
        SignalRank rank = toRank(signalScore, crossSourceConfirmed);

        List<SourceDomain> sortedDomains = domains.stream()
                .sorted(Comparator.comparing(SourceDomain::name))
                .toList();

        // Keep every component instead of discarding it: the digest owes the reader an explanation of
        // why an item surfaced, and of what his own votes did to it.
        ScoreBreakdown breakdown = new ScoreBreakdown(
                SourceWeights.keyOf(item.source()), SourceWeights.of(item.source()), weight, netVotes,
                engagementBonus, preference,
                CategoryPreference.netVotesFor(item.category(), netVotesByCategory), crossSourceBonus, mute);

        return new Signal(item, rank, signalScore, sortedDomains, null, null, breakdown);
    }

    /**
     * Corroboration decides CRITICAL; points decide everything below it. A confirmed story is
     * CRITICAL even when three weak sources add up to little, and an unconfirmed one is not, however
     * far past the old 101-point threshold its engagement carries it.
     */
    private SignalRank toRank(int score, boolean crossSourceConfirmed) {
        if (crossSourceConfirmed) {
            return SignalRank.CRITICAL;
        }
        if (score >= policy.strongThreshold()) {
            return SignalRank.STRONG;
        }
        if (score >= policy.moderateThreshold()) {
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
