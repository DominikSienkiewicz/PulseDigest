package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N21. Vectors are supplied rather than fetched, so every case here is deterministic and the
 * thresholds are being tested rather than the provider's embedding of the day.
 */
class SemanticClusteringServiceTest {

    private static final float[] LOOM_A = {1.00f, 0.00f, 0.00f};
    private static final float[] LOOM_B = {0.97f, 0.24f, 0.00f};   // ~0.97 cosine to LOOM_A
    private static final float[] LOOM_C = {0.94f, 0.34f, 0.00f};   // ~0.94 to A, ~0.998 to B
    private static final float[] UNRELATED = {0.00f, 0.00f, 1.00f};

    @Test
    void shadowModeFindsTheClusterAndChangesNothing() {
        var service = service(false, List.of(LOOM_A, LOOM_B, UNRELATED));

        List<DigestItem> out = service.cluster(items(3));

        assertThat(out).extracting(DigestItem::topicKey).containsOnlyNulls();
    }

    @Test
    void enforcingModeGivesOneStoryOneKey() {
        var service = service(true, List.of(LOOM_A, LOOM_B, UNRELATED));

        List<DigestItem> out = service.cluster(items(3));

        assertThat(out.get(0).correlationKey()).isEqualTo(out.get(1).correlationKey());
        assertThat(out.get(2).topicKey())
                .as("an unrelated item keeps no key and falls back to its category")
                .isNull();
    }

    @Test
    void itemsBelowTheThresholdAreLeftApart() {
        var service = service(true, List.of(LOOM_A, UNRELATED));

        List<DigestItem> out = service.cluster(items(2));

        assertThat(out).extracting(DigestItem::topicKey).containsOnlyNulls();
    }

    @Test
    void similarityIsMeasuredAgainstTheSeedSoClustersCannotChain() {
        // The classic over-merge: A~B and B~C, but A and C are different stories. Comparing every
        // candidate against the cluster's seed is what stops a chain of near-misses becoming one
        // story — and here, a corroboration that never happened.
        var service = new SemanticClusteringService(texts -> List.of(LOOM_A, LOOM_B, LOOM_C),
                new SemanticClusteringPolicy(true, true, 0.96, 4));

        List<DigestItem> out = service.cluster(items(3));

        assertThat(out.get(0).correlationKey()).isEqualTo(out.get(1).correlationKey());
        assertThat(out.get(2).topicKey())
                .as("C is 0.998 similar to B but only 0.94 to the seed, so it stays out")
                .isNull();
    }

    @Test
    void aClusterLargerThanTheCapIsRejectedWhole() {
        // A dozen identical headlines is a threshold problem, not a very well-covered story, and
        // keeping an arbitrary subset would hide that rather than fix it.
        var service = new SemanticClusteringService(
                texts -> List.of(LOOM_A, LOOM_A, LOOM_A, LOOM_A, LOOM_A),
                new SemanticClusteringPolicy(true, true, 0.86, 3));

        assertThat(service.cluster(items(5))).extracting(DigestItem::topicKey).containsOnlyNulls();
    }

    @Test
    void aFailedEmbeddingCallLeavesTheEditionAlone() {
        // Clustering on partial information merges the wrong things, which is the one outcome worse
        // than not clustering at all.
        var service = service(true, List.of());

        assertThat(service.cluster(items(3))).extracting(DigestItem::topicKey).containsOnlyNulls();
    }

    @Test
    void theEmbedderIsNotEvenCalledWhenClusteringIsOff() {
        var service = new SemanticClusteringService(texts -> {
            throw new AssertionError("must not embed when disabled");
        }, new SemanticClusteringPolicy(false, false, 0.86, 4));

        assertThat(service.cluster(items(3))).hasSize(3);
    }

    @Test
    void oneItemNeedsNoEmbeddingAtAll() {
        var service = new SemanticClusteringService(texts -> {
            throw new AssertionError("must not embed a single item");
        }, new SemanticClusteringPolicy(true, true, 0.86, 4));

        assertThat(service.cluster(items(1))).hasSize(1);
    }

    @Test
    void cosineIsSafeOnTheShapesAProviderCanReturn() {
        assertThat(SemanticClusteringService.cosine(null, LOOM_A)).isZero();
        assertThat(SemanticClusteringService.cosine(new float[]{0, 0, 0}, LOOM_A)).isZero();
        assertThat(SemanticClusteringService.cosine(new float[]{1, 0}, LOOM_A)).isZero();
        assertThat(SemanticClusteringService.cosine(LOOM_A, LOOM_A)).isCloseTo(1.0, within(1e-9));
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }

    private static SemanticClusteringService service(boolean enforcing, List<float[]> vectors) {
        return new SemanticClusteringService(texts -> vectors,
                new SemanticClusteringPolicy(true, enforcing, 0.86, 4));
    }

    private static List<DigestItem> items(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> TestModels.digestItem("Story " + i, "https://example.com/" + i,
                        "Source " + i, "Java/JVM", "NEWS", 8, null, "summary " + i, "why"))
                .toList();
    }
}
