package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.Comparator;
import java.util.Map;

/**
 * Source credibility weights for deterministic signal scoring.
 * Shared between pre-LLM noise reduction and post-LLM signal scoring.
 */
public final class SourceWeights {

    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
            Map.entry("arXiv",               1.00),
            Map.entry("GitHub Releases",     0.95),
            Map.entry("Security Advisories", 0.90),
            Map.entry("NVD/CVE",             0.90),
            Map.entry("GitHub",              0.85),
            Map.entry("Hacker News",         0.80),
            Map.entry("Tech Radar",          0.80),
            Map.entry("OpenJDK JEP",         0.75),
            Map.entry("CNCF",                0.75),
            Map.entry("Reddit",              0.60),
            Map.entry("Product Hunt",        0.55),
            Map.entry("YouTube",             0.55),
            Map.entry("Hugging Face",        0.50),
            Map.entry("RSS",                 0.45),
            Map.entry("Twitter/X",           0.40),
            Map.entry("Libraries.io",        0.40),
            Map.entry("DB-Engines",          0.40)
    );

    private static final double DEFAULT = 0.30;

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
}
