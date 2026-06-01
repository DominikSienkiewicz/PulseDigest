package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure, framework-free counting of technology mentions across hiring posts.
 *
 * <p>Counts, for each vocabulary token, how many of the given texts mention it (at most once per
 * text — a post that lists "Kubernetes" three times still counts as one hiring opportunity for
 * Kubernetes). Matching is case-insensitive and boundary-aware so short tokens like {@code go} or
 * {@code react} do not match inside {@code google} or {@code goal}.
 */
public final class TechDemandAggregator {

    private TechDemandAggregator() {
    }

    /**
     * @param texts      one entry per hiring post (HN comment); null/blank entries are ignored
     * @param vocabulary technology tokens to count
     * @return map of token → number of posts mentioning it (only tokens with ≥ 1 mention)
     */
    public static Map<String, Integer> countMentions(List<String> texts, List<String> vocabulary) {
        if (texts == null || texts.isEmpty() || vocabulary == null || vocabulary.isEmpty()) {
            return Map.of();
        }
        List<String> lowered = texts.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String token : vocabulary) {
            if (token == null || token.isBlank()) {
                continue;
            }
            Pattern pattern = boundaryPattern(token.toLowerCase(Locale.ROOT));
            int mentions = (int) lowered.stream().filter(text -> pattern.matcher(text).find()).count();
            if (mentions > 0) {
                counts.put(token, mentions);
            }
        }
        return counts;
    }

    // Boundaries are non-alphanumeric so "go"/"react" match as whole words but ".net"/"node.js" still work.
    private static Pattern boundaryPattern(String token) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(token) + "(?![a-z0-9])");
    }
}
