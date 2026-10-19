package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmbeddingPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the same story told under three headlines, and gives the three one correlation key (N21).
 *
 * <p>This is what makes 🔴 reachable. Since F2 the badge means corroboration and nothing else, and
 * corroboration was hard to satisfy by construction: the same release announced on a blog, a
 * subreddit and Hacker News produced three unrelated items because nothing matched them. Clustering
 * does not add a new ranking signal — it reveals the confirmations that were already there.
 *
 * <p><strong>It assigns topic keys and stops.</strong> Everything downstream already knows what to
 * do with a shared key: {@code DigestLayout} folds them into one story, {@code DigestStory} lists
 * their distinct sources, and {@code SignalScoringService} counts the distinct source domains. A
 * clustering pass that also rendered, scored or merged would be re-implementing three things that
 * work.
 *
 * <p>Ships in <strong>shadow mode</strong>. Over-merging is worse than duplication, and worse here
 * than elsewhere: a wrong merge does not just lose a line, it manufactures a corroboration, and
 * therefore a 🔴, out of two unrelated articles. The log names every group it would form so the
 * threshold can be tuned against real editions — which is what {@code --replay} is for — before it
 * is allowed to change anything.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SemanticClusteringService {

    private final EmbeddingPort embeddingPort;
    private final SemanticClusteringPolicy policy;

    /** @return the items, with clustered ones sharing a topic key when the policy is enforcing */
    public List<DigestItem> cluster(List<DigestItem> items) {
        if (!policy.enabled() || items == null || items.size() < 2) {
            return items;
        }
        List<float[]> vectors = embeddingPort.embed(items.stream().map(
                SemanticClusteringService::textOf).toList());
        if (vectors.size() != items.size()) {
            // Partial vectors would compare items against the wrong neighbours. Nothing is better.
            log.warn("semantic-clustering skipped: expected {} vectors, got {}",
                    items.size(), vectors.size());
            return items;
        }

        List<List<Integer>> clusters = group(vectors);
        List<List<Integer>> usable = clusters.stream()
                .filter(cluster -> cluster.size() > 1)
                .filter(this::withinSizeLimit)
                .toList();
        if (usable.isEmpty()) {
            log.info("semantic-clustering mode={} clusters=0", mode());
            return items;
        }

        usable.forEach(cluster -> log.info("semantic-clustering would-merge key={} items={}",
                keyFor(items, cluster),
                cluster.stream().map(i -> items.get(i).title()).toList()));

        if (!policy.enforcing()) {
            log.info("semantic-clustering mode=shadow clusters={} — recorded, not applied", usable.size());
            return items;
        }
        return applyKeys(items, usable);
    }

    /**
     * A cluster larger than the cap is rejected whole rather than trimmed. A dozen headlines that
     * all look identical is a threshold that has gone wrong, and keeping an arbitrary subset of a
     * bad grouping would hide that rather than fix it.
     */
    private boolean withinSizeLimit(List<Integer> cluster) {
        if (cluster.size() <= policy.maxClusterSize()) {
            return true;
        }
        log.warn("semantic-clustering rejected a cluster of {} (limit {}) — threshold is too loose",
                cluster.size(), policy.maxClusterSize());
        return false;
    }

    /**
     * Greedy single-pass grouping: each item joins the first cluster whose <em>first</em> member it
     * is similar enough to.
     *
     * <p>Compared against the seed rather than against any member on purpose. Chaining — A matches
     * B, B matches C, so A and C are one story even though they are not similar — is the classic way
     * clustering over-merges, and it is exactly the failure that would invent a corroboration here.
     */
    private List<List<Integer>> group(List<float[]> vectors) {
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            boolean joined = false;
            for (List<Integer> cluster : clusters) {
                if (cosine(vectors.get(cluster.getFirst()), vectors.get(i)) >= policy.threshold()) {
                    cluster.add(i);
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                clusters.add(new ArrayList<>(List.of(i)));
            }
        }
        return clusters;
    }

    private static List<DigestItem> applyKeys(List<DigestItem> items, List<List<Integer>> clusters) {
        Map<Integer, String> keys = new LinkedHashMap<>();
        clusters.forEach(cluster -> cluster.forEach(index -> keys.put(index, keyFor(items, cluster))));
        List<DigestItem> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String key = keys.get(i);
            out.add(key == null ? items.get(i) : items.get(i).withTopicKey(key));
        }
        return List.copyOf(out);
    }

    /**
     * The cluster's key: the URL of its seed. Stable, unique, and already the thing the rest of the
     * pipeline treats as an opaque correlation key.
     */
    private static String keyFor(List<DigestItem> items, List<Integer> cluster) {
        return "cluster:" + items.get(cluster.getFirst()).url();
    }

    /** Title and summary — what the story is, not where it was published. */
    private static String textOf(DigestItem item) {
        String title = item.title() == null ? "" : item.title();
        String summary = item.summary() == null ? "" : item.summary();
        return (title + ". " + summary).strip();
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String mode() {
        return policy.enforcing() ? "enforcing" : "shadow";
    }
}
