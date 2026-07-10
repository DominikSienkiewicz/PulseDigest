package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.Comparator;
import java.util.Map;

/**
 * Source credibility weights for deterministic signal scoring.
 * Shared between pre-LLM noise reduction and post-LLM signal scoring.
 */
public final class SourceWeights {

    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
            // AI-lab announcements are the primary source for model/product news — the lab itself
            // saying it, not someone reporting it. Keyed by lab prefix so every per-blog label
            // ("Anthropic News", "Anthropic Engineering", …) resolves to one weight and one
            // feedback bucket. "OpenAI" does not collide with the exact key "OpenJDK JEP".
            Map.entry("Anthropic",           0.95),
            Map.entry("Claude",              0.95),
            Map.entry("Google Gemini",       0.95),
            Map.entry("OpenAI",              0.95),
            // arXiv demoted from 1.00 — research papers are a background signal for this audience,
            // not the priority. Usable tools/launches (Product Hunt, Hugging Face) are upweighted instead.
            Map.entry("arXiv",               0.70),
            Map.entry("GitHub Releases",     0.95),
            // Security is a background topic for this audience — generic advisory feeds are demoted to
            // the default floor (0.30) so they neither flood the pre-LLM prompt nor reach a high signal
            // rank. Stack-relevant advisories (maven/pip/docker/actions) still surface via their LLM score.
            Map.entry("Security Advisories", 0.30),
            Map.entry("GitHub",              0.85),
            Map.entry("Hacker News",         0.80),
            Map.entry("Tech Radar",          0.80),
            Map.entry("OpenJDK JEP",         0.75),
            Map.entry("CNCF",                0.75),
            Map.entry("Reddit",              0.60),
            Map.entry("Product Hunt",        0.75),
            Map.entry("YouTube",             0.55),
            Map.entry("Hugging Face",        0.70),
            Map.entry("RSS",                 0.45),
            Map.entry("Twitter/X",           0.40),
            Map.entry("Bluesky",             0.45),
            Map.entry("Mastodon",            0.45)
    );

    private static final double DEFAULT = 0.30;

    // C6 feedback nudging: net (UP − DOWN) reader votes per source shift its weight by this step each,
    // bounded so accumulated feedback re-ranks a source but never overwhelms its base credibility.
    private static final double FEEDBACK_STEP = 0.05;
    private static final double MAX_FEEDBACK_DELTA = 0.30;
    private static final double MIN_WEIGHT = 0.10;
    // Capped just below 1.0 so a positive nudge alone can never reach the STRONG base score (round(1.0*100)=100):
    // feedback re-orders within a rank and demotes noise; cross-source / engagement bonuses still own promotion.
    private static final double MAX_WEIGHT = 0.99;

    private SourceWeights() {
    }

    /**
     * Returns the credibility weight for the given source.
     * Exact match first; then longest prefix match (deterministic for ambiguous prefixes
     * such as "GitHub" vs "GitHub Releases"); unknown or null source returns 0.30.
     */
    public static double of(String source) {
        if (source == null) {
            return DEFAULT;
        }
        Double exact = WEIGHTS.get(source);
        if (exact != null) {
            return exact;
        }
        return WEIGHTS.entrySet().stream()
                .filter(e -> source.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(DEFAULT);
    }

    /**
     * Resolves a source label to the base-weight key it maps to — the same exact-then-longest-prefix
     * resolution {@link #of(String)} uses. High-cardinality labels collapse to their base source
     * ({@code "arXiv/cs.AI"} → {@code "arXiv"}, {@code "Reddit/r/java"} → {@code "Reddit"}), so feedback
     * votes aggregate at the same granularity as the weight they nudge. Unknown sources map to
     * themselves (their own bucket); {@code null} maps to the empty string.
     *
     * @param source the item source label
     * @return the base-weight key this source resolves to
     */
    public static String keyOf(String source) {
        if (source == null) {
            return "";
        }
        if (WEIGHTS.containsKey(source)) {
            return source;
        }
        return WEIGHTS.entrySet().stream()
                .filter(e -> source.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getKey)
                .orElse(source);
    }

    /**
     * Returns the base credibility weight nudged by accumulated reader feedback (C6). A source's net
     * vote count (UP minus DOWN over the feedback window) shifts its weight by {@code 0.05} per vote,
     * the shift clamped to {@code ±0.30} and the result to {@code [0.10, 0.99]}. With zero votes this
     * is identical to {@link #of(String)}.
     *
     * @param source            the item source label (same label the email feedback links carry)
     * @param netFeedbackVotes  net (UP − DOWN) votes for this source within the feedback window
     * @return the feedback-adjusted credibility weight
     */
    public static double of(String source, int netFeedbackVotes) {
        double delta = clamp(netFeedbackVotes * FEEDBACK_STEP, -MAX_FEEDBACK_DELTA, MAX_FEEDBACK_DELTA);
        return clamp(of(source) + delta, MIN_WEIGHT, MAX_WEIGHT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }
}
