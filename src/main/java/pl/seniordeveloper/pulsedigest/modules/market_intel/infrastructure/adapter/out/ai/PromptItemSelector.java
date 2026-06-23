package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceWeights;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Picks the best items per source (by engagement desc), then trims to a hard total cap using a
 * weighted pre-score so a single noisy source cannot flood the LLM prompt.
 *
 * <p>Extracted from {@link ReportPromptBuilder} to keep that class focused on payload assembly
 * and to make the cap-and-trim logic independently testable.
 */
final class PromptItemSelector {

    private static final int CAP_RSS         = 30;
    private static final int CAP_TWITTER     = 20;
    private static final int CAP_REDDIT      = 15;
    private static final int CAP_HN          = 10;
    private static final int CAP_GITHUB      = 10;
    private static final int CAP_ARXIV       = 8;
    private static final int CAP_RELEASES    = 10;
    private static final int CAP_HF          = 8;
    private static final int CAP_PRODUCTHUNT = 6;
    // Security is a background topic for this audience (JVM / Python-AI / Cloud-Native): keep the
    // deterministic intake small so generic advisory feeds cannot crowd out priority content.
    // Stack-relevant advisories still surface — the LLM scores them on merit (see system-prompt.txt).
    private static final int CAP_ADVISORIES  = 3;
    private static final int CAP_JEP         = 5;
    private static final int CAP_CNCF        = 5;
    // Tech Radar (quarterly) and conference talks are low-cadence background sources for this
    // audience: capped tight so they never crowd out fresh JVM / AI / cloud-native signal.
    private static final int CAP_RADAR       = 2;
    private static final int CAP_TALKS       = 3;
    // Free-source recovery (Bluesky + Mastodon) — recovers CV-relevant signal cut off the X budget.
    private static final int CAP_SOCIAL      = 12;
    static final int TOTAL_CAP               = 100;

    private PromptItemSelector() {
    }

    static List<Map<String, Object>> selectTopItems(List<Map<String, Object>> all) {
        List<Map<String, Object>> twitter     = new ArrayList<>();
        List<Map<String, Object>> hn          = new ArrayList<>();
        List<Map<String, Object>> github      = new ArrayList<>();
        List<Map<String, Object>> rss         = new ArrayList<>();
        List<Map<String, Object>> reddit      = new ArrayList<>();
        List<Map<String, Object>> arxiv       = new ArrayList<>();
        List<Map<String, Object>> releases    = new ArrayList<>();
        List<Map<String, Object>> hf          = new ArrayList<>();
        List<Map<String, Object>> productHunt = new ArrayList<>();
        List<Map<String, Object>> advisories  = new ArrayList<>();
        List<Map<String, Object>> jep         = new ArrayList<>();
        List<Map<String, Object>> cncf        = new ArrayList<>();
        List<Map<String, Object>> radar       = new ArrayList<>();
        List<Map<String, Object>> talks       = new ArrayList<>();
        List<Map<String, Object>> social      = new ArrayList<>();

        // Ordered first-match-wins routing (preserves the original if/else-if precedence).
        List<Route> routes = List.of(
                new Route(s -> s.startsWith("Twitter"), twitter),
                new Route(s -> s.startsWith("Hacker News"), hn),
                new Route(s -> s.equals("GitHub"), github),
                new Route(s -> s.startsWith("RSS"), rss),
                new Route(s -> s.startsWith("Reddit"), reddit),
                new Route(s -> s.startsWith("arXiv"), arxiv),
                new Route(s -> s.equals("GitHub Releases"), releases),
                new Route(s -> s.equals("Hugging Face"), hf),
                new Route(s -> s.equals("Product Hunt"), productHunt),
                new Route(s -> s.equals("Security Advisories"), advisories),
                new Route(s -> s.equals("OpenJDK JEP"), jep),
                new Route(s -> s.startsWith("CNCF"), cncf),
                new Route(s -> s.equals("Tech Radar"), radar),
                new Route(s -> s.startsWith("YouTube"), talks),
                new Route(s -> s.equals("Bluesky") || s.equals("Mastodon"), social));

        for (var item : all) {
            String src = (String) item.get("source");
            for (Route route : routes) {
                if (route.match().test(src)) {
                    route.bucket().add(item);
                    break;
                }
            }
        }

        Comparator<Map<String, Object>> byEngagement =
                Comparator.comparingInt(m -> -((Number) m.get("engagement_score")).intValue());

        List<Map<String, Object>> selected = new ArrayList<>();
        selected.addAll(topN(twitter,     CAP_TWITTER,     byEngagement));
        selected.addAll(topN(hn,          CAP_HN,          byEngagement));
        selected.addAll(topN(github,      CAP_GITHUB,      byEngagement));
        selected.addAll(topN(rss,         CAP_RSS,         byEngagement));
        selected.addAll(topN(reddit,      CAP_REDDIT,      byEngagement));
        selected.addAll(topN(arxiv,       CAP_ARXIV,       byEngagement));
        selected.addAll(topN(releases,    CAP_RELEASES,    byEngagement));
        selected.addAll(topN(hf,          CAP_HF,          byEngagement));
        selected.addAll(topN(productHunt, CAP_PRODUCTHUNT, byEngagement));
        selected.addAll(topN(advisories,  CAP_ADVISORIES,  byEngagement));
        selected.addAll(topN(jep,         CAP_JEP,         byEngagement));
        selected.addAll(topN(cncf,        CAP_CNCF,        byEngagement));
        selected.addAll(topN(radar,       CAP_RADAR,       byEngagement));
        selected.addAll(topN(talks,       CAP_TALKS,       byEngagement));
        selected.addAll(topN(social,      CAP_SOCIAL,      byEngagement));

        return applyTotalCap(selected, TOTAL_CAP);
    }

    private static List<Map<String, Object>> topN(
            List<Map<String, Object>> items,
            int n,
            Comparator<Map<String, Object>> comparator) {
        return items.stream()
                .sorted(comparator)
                .limit(n)
                .toList();
    }

    /**
     * Pre-score for overflow trimming: base weight (30–100) + engagement bonus (0–50 via integer
     * division by 1_000). Cross-source bonus is intentionally excluded — category context is not
     * available at pre-LLM selection time.
     */
    static int preScore(String source, int engagement) {
        return (int) Math.round(SourceWeights.of(source) * 100)
                + Math.min(50, engagement / 1_000);
    }

    /**
     * Keeps the top {@code cap} items ranked by pre-score descending; returns {@code selected}
     * unchanged if already at or under cap.
     */
    static List<Map<String, Object>> applyTotalCap(List<Map<String, Object>> selected, int cap) {
        if (selected.size() <= cap) {
            return selected;
        }
        return selected.stream()
                .sorted(Comparator.comparingInt(
                        (Map<String, Object> m) -> -preScore(
                                (String) m.get("source"),
                                ((Number) m.get("engagement_score")).intValue())))
                .limit(cap)
                .toList();
    }

    private record Route(Predicate<String> match, List<Map<String, Object>> bucket) {
    }
}
