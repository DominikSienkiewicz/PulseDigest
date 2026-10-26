package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SemanticClusteringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N21's "done when", end to end: three headlines of one story become one item naming three sources.
 *
 * <p>Built on the committed edition rather than on three invented items, so the assertion runs
 * through the same layout, story folding and scoring a real run uses. The vectors are supplied —
 * what is under test is the pipeline's behaviour given a cluster, not the provider's embedding.
 *
 * <p>This is also where the feature's <em>point</em> is visible. Since F2, 🔴 means corroboration
 * from three distinct source domains and nothing else, and three unmatched items can never reach
 * it however popular they are. Clustering is what turns coverage that was already there into a
 * badge that means something.
 */
class SemanticClusterRenderingTest {

    /** Three near-identical vectors and one unrelated, in the order the items are built. */
    private static final List<float[]> VECTORS = List.of(
            new float[]{1.00f, 0.00f, 0.00f},
            new float[]{0.98f, 0.17f, 0.00f},
            new float[]{0.97f, 0.22f, 0.00f},
            new float[]{0.00f, 0.00f, 1.00f});

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void threeHeadlinesOfOneStoryRenderAsOneItemNamingThreeSources() {
        List<DigestItem> clustered = clusterer(true).cluster(threeTellingsAndOneOther());

        // Across every section: a folded story is promoted by corroboration, so the cluster and the
        // story it did not touch deliberately end up in different parts of the digest.
        List<DigestStory> stories = allStories(DigestLayout.of(clustered, score(clustered)));

        DigestStory folded = stories.stream()
                .filter(story -> story.sources().size() > 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no story folded three tellings together"));

        assertThat(folded.sources())
                .as("one item, three sources")
                .containsExactlyInAnyOrder("Claude Blog", "Hacker News", "Mastodon");
        assertThat(stories)
                .as("four items became two stories: the cluster, and the one it left alone")
                .hasSize(2);
        assertThat(stories.stream().filter(story -> story.sources().size() == 1).count())
                .as("the unrelated story stays its own")
                .isEqualTo(1);
    }

    @Test
    void theRenderedDigestShowsOneHeadlineAndNamesTheOtherTwoSources() {
        List<DigestItem> clustered = clusterer(true).cluster(threeTellingsAndOneOther());
        ReportData report = new ReportData(fixture.report().emailPreview(), fixture.report().editorial(),
                fixture.report().topInsights(), clustered).withSignals(score(clustered));

        String html = new ReportEmailBuilder(
                new FeedbackProperties(false, 30, "", ""),
                new ThinWeekProperties(false, 3),
                new SectionsPolicy(Set.of())).buildHtml(report, fixture.research());

        assertThat(html).contains("Potwierdzone w:");
        assertThat(countOccurrences(html, "Rejestr MCP &mdash; wersjonowane manifesty"))
                .as("the folded tellings are not each rendered as their own item")
                .isZero();
    }

    @Test
    void withoutClusteringTheSameThreeStayThreeSeparateItems() {
        // The before picture, and the reason the feature exists: three unmatched items cannot reach
        // 🔴 however many sources carried the story, because nothing knows they are one story.
        List<DigestItem> unclustered = threeTellingsAndOneOther();

        long folded = allStories(DigestLayout.of(unclustered, score(unclustered))).stream()
                .filter(story -> story.sources().size() > 1)
                .count();

        assertThat(folded).isZero();
    }

    /** Every story the layout placed, wherever it placed it. */
    private static List<DigestStory> allStories(DigestLayout layout) {
        return java.util.stream.Stream.of(layout.criticalTrends(), layout.mustKnow(), layout.deals(),
                        layout.topPicks(), layout.signals())
                .flatMap(List::stream)
                .toList();
    }

    /** One real story from the committed edition, as three sources would have told it. */
    private List<DigestItem> threeTellingsAndOneOther() {
        DigestItem real = fixture.report().items().getFirst();
        return List.of(
                real,
                telling("Rejestr MCP — wersjonowane manifesty", "https://news.ycombinator.com/item?id=1",
                        "Hacker News"),
                telling("MCP dostaje rejestr narzędzi", "https://mastodon.social/@x/2", "Mastodon"),
                telling("Zupełnie inna historia o bazach danych", "https://example.com/other",
                        "Product Hunt"));
    }

    private static DigestItem telling(String title, String url, String source) {
        return TestModels.digestItem(title, url, source, "AI/LLM", "NEWS", 9, 40,
                "Ta sama zapowiedź rejestru MCP, opisana gdzie indziej.", "why");
    }

    private static List<Signal> score(List<DigestItem> items) {
        return new SignalScoringService(ScoringPolicy.defaults()).score(items, Map.of(), Map.of());
    }

    private static SemanticClusteringService clusterer(boolean enforcing) {
        return new SemanticClusteringService(texts -> VECTORS,
                new SemanticClusteringPolicy(true, enforcing, 0.90, 4));
    }

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
