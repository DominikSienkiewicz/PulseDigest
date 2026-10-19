package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * How aggressively the same story told twice is collapsed into one (N21).
 *
 * <p>Ships enabled but <strong>not</strong> enforcing, for the reason the catalogue names:
 * over-merging distinct stories is worse than duplication. A duplicate costs the reader a line; a
 * wrong merge invents a corroboration that never happened — and since F2, corroboration is the only
 * route to 🔴, so a wrong merge manufactures a critical trend out of two unrelated articles.
 *
 * @param enabled     whether items are embedded and compared at all
 * @param enforcing   whether a cluster actually rewrites topic keys, or is only logged
 * @param threshold   cosine similarity above which two items are the same story
 * @param maxClusterSize largest group a single story may form; beyond it the cluster is rejected
 *                       whole, because a dozen "identical" headlines is a threshold problem rather
 *                       than a very well-covered story
 */
public record SemanticClusteringPolicy(
        boolean enabled, boolean enforcing, double threshold, int maxClusterSize) {
}
