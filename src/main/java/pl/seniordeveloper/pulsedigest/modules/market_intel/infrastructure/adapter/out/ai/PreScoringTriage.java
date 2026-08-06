package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Trims the prompt payload to the items a cheap triage model rated highest, so the expensive model
 * does not pay full rate to read noise it will discard anyway.
 *
 * <p>Two guards keep the saving from turning into a quality regression. A source whose deterministic
 * pre-score clears {@link #FLOOR_PRE_SCORE} is immune: the triage model has no idea what
 * {@code owner/some-niche-lib v3.0} is, but a GitHub release at weight 0.95 is exactly the item this
 * reader wants. And an empty score map — a failed or disabled triage call — passes everything through
 * rather than shrinking the digest because of an infrastructure hiccup.
 */
final class PreScoringTriage {

    /** Deterministic pre-score at or above which an item cannot be triaged away (GitHub Releases, labs). */
    private static final int FLOOR_PRE_SCORE = 90;
    private static final int NEUTRAL_SCORE = 5;

    private PreScoringTriage() {
    }

    static List<Map<String, Object>> triage(List<Map<String, Object>> payload,
                                            Map<String, Integer> triageScores,
                                            int keep) {
        if (triageScores.isEmpty() || payload.size() <= keep) {
            return payload;
        }
        return payload.stream()
                .sorted(Comparator.comparingInt((Map<String, Object> item) -> -rank(item, triageScores)))
                .limit(keep)
                .toList();
    }

    // Protected items sort above everything a triage model could award, so they always survive.
    private static int rank(Map<String, Object> item, Map<String, Integer> triageScores) {
        if (isProtected(item)) {
            return Integer.MAX_VALUE;
        }
        return triageScores.getOrDefault(item.get("url"), NEUTRAL_SCORE);
    }

    private static boolean isProtected(Map<String, Object> item) {
        int deterministic = PromptItemSelector.preScore(
                (String) item.get("source"),
                ((Number) item.get("engagement_score")).intValue());
        return deterministic >= FLOOR_PRE_SCORE;
    }
}
