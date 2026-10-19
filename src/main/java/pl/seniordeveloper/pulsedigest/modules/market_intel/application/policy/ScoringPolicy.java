package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import java.util.Locale;
import java.util.Map;

/**
 * The numbers that decide what a signal is worth, and how a source label maps to a domain (N30).
 *
 * <p>These were {@code private static final} constants in {@code SignalScoringService}. That made
 * the one experiment F2 leaves open — does corroboration across *two* independent domains mean
 * enough to be 🔴, or does it take three? — a recompile each time, which is how an experiment stops
 * happening. The thresholds that define the headline feature are exactly the ones worth being able
 * to move.
 *
 * <p>{@code domainOverrides} remaps a source label prefix onto a different domain, which is what
 * makes the other half of F2's problem testable: {@code SourceDomain.from} collapses RSS, Reddit,
 * Twitter, Bluesky, Mastodon and every unrecognised label into {@code SOCIAL}, so a story carried by
 * three of those counts as one domain. Moving a few of them elsewhere can be tried from
 * configuration now. What configuration *cannot* do is invent a new domain: splitting {@code SOCIAL}
 * into {@code SOCIAL} and {@code PRESS} needs a new enum constant, and that stays a code change.
 */
public record ScoringPolicy(
        int crossSourceThreshold,
        int crossSourceBonus,
        int maxEngagementBonus,
        int engagementDivisor,
        int strongThreshold,
        int moderateThreshold,
        Map<String, SourceDomain> domainOverrides,
        java.util.Set<String> mutedTopics,
        double muteMultiplier
) {

    public ScoringPolicy {
        domainOverrides = domainOverrides != null ? Map.copyOf(domainOverrides) : Map.of();
        mutedTopics = mutedTopics != null
                ? mutedTopics.stream().filter(t -> t != null && !t.isBlank())
                        .map(t -> t.strip().toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())
                : java.util.Set.of();
        muteMultiplier = muteMultiplier > 0 ? muteMultiplier : 1.0;
    }

    /** The values the scorer used while they were constants — the behaviour this change preserves. */
    public static ScoringPolicy defaults() {
        return new ScoringPolicy(3, 50, 50, 1_000, 100, 60, Map.of(), java.util.Set.of(), 0.5);
    }

    /**
     * Whether the reader has said outright that they do not want this topic (N13).
     *
     * <p>Matched against the item's category and its topic key, because a reader muting "crypto"
     * should not have to know which of the two the model happened to put it in.
     *
     * <p>A mute lowers the score and <b>cannot</b> prevent CRITICAL. That used to need stating as a
     * rule — "never apply the multiplier to the cross-source bonus" — and since F2 it is structural:
     * rank is decided by how many independent domains carried the story, not by points. A muted
     * topic that three domains confirm still reaches 🔴, which is what keeps an explicit preference
     * from becoming a filter bubble the topic can never climb out of. Feedback-driven preference has
     * the same guarantee for the same reason.
     */
    public boolean isMuted(String category, String topicKey) {
        if (mutedTopics.isEmpty()) {
            return false;
        }
        return matches(category) || matches(topicKey);
    }

    private boolean matches(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String needle = value.strip().toLowerCase(Locale.ROOT);
        return mutedTopics.stream().anyMatch(needle::contains);
    }

    /**
     * The domain a source label belongs to: a configured override when one matches, otherwise the
     * built-in mapping. Longest prefix wins, so a specific override beats a broader one rather than
     * depending on map iteration order.
     */
    public SourceDomain domainOf(String source) {
        if (source == null || domainOverrides.isEmpty()) {
            return SourceDomain.from(source);
        }
        String label = source.toLowerCase(Locale.ROOT);
        return domainOverrides.entrySet().stream()
                .filter(e -> label.startsWith(e.getKey().toLowerCase(Locale.ROOT)))
                .max(java.util.Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElseGet(() -> SourceDomain.from(source));
    }

    /** Whether this many distinct domains counts as corroboration. */
    public boolean isCrossSourceConfirmed(int distinctDomains) {
        return distinctDomains >= crossSourceThreshold;
    }
}
