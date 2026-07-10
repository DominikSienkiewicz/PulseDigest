package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.Locale;
import java.util.Map;

/**
 * Turns accumulated 👍/👎 votes on a topic category into a score multiplier.
 *
 * <p>A down-vote on a dull paper should punish the topic ("Research"), not the whole of arXiv — which
 * is what per-source nudging does today. Half the information in every click was being discarded.
 *
 * <p>The multiplier is clamped to a deliberately narrow band. A category the reader has muted must
 * keep surfacing often enough to earn the votes that would revive it; a scoring signal that can bury
 * a category outright is a positive feedback loop, not a preference. {@code SignalScoringService}
 * applies this multiplier to the credibility and engagement components only — never to the
 * cross-source bonus, so a muted category can still break through as a Critical Trend when three
 * independent domains confirm one story. Corroboration is evidence, not taste.
 */
public final class CategoryPreference {

    /** Per net vote. Twelve consistent votes reach the cap; a stray click moves nothing that matters. */
    private static final double STEP = 0.02;
    private static final double MIN_MULTIPLIER = 0.90;
    private static final double MAX_MULTIPLIER = 1.10;
    /** Net votes below this are noise, not a preference worth telling the model about. */
    private static final int EXPRESSED_THRESHOLD = 3;

    private CategoryPreference() {
    }

    /** Score multiplier for a category with the given net (UP − DOWN) vote count. */
    public static double multiplier(int netVotes) {
        return Math.clamp(1.0 + STEP * netVotes, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    /** Multiplier for {@code category}, looked up case-insensitively; neutral when unknown or null. */
    public static double multiplierFor(String category, Map<String, Integer> netVotesByCategory) {
        return multiplier(netVotesFor(category, netVotesByCategory));
    }

    /** Net votes for {@code category}; zero when unknown or null. */
    public static int netVotesFor(String category, Map<String, Integer> netVotesByCategory) {
        if (category == null || netVotesByCategory == null) {
            return 0;
        }
        return netVotesByCategory.getOrDefault(category.toLowerCase(Locale.ROOT), 0);
    }

    /** Whether the reader has voted on this category enough times for it to be a stated preference. */
    public static boolean isExpressed(int netVotes) {
        return Math.abs(netVotes) >= EXPRESSED_THRESHOLD;
    }
}
