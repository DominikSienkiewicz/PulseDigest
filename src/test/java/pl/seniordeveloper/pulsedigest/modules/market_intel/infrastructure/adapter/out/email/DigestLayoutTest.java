package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DigestLayoutTest {

    @Test
    void collapsesItemsSharingATopicKeyIntoOneStoryNamingEverySource() {
        DigestLayout layout = DigestLayout.of(
                List.of(item("Registry launched", "https://lab", "Claude Blog", "AI/LLM", "LAUNCH", 9, "mcp"),
                        item("registry repo", "https://gh", "GitHub", "AI/LLM", "TREND", 8, "mcp"),
                        item("registry thread", "https://hn", "Hacker News", "AI/LLM", "DISCUSSION", 7, "mcp")),
                List.of());

        assertThat(all(layout)).hasSize(1);
        DigestStory story = all(layout).getFirst();
        assertThat(story.lead().url()).isEqualTo("https://lab");
        assertThat(story.sources()).containsExactly("Claude Blog", "GitHub", "Hacker News");
        assertThat(story.isConfirmed()).isTrue();
    }

    @Test
    void leadsAStoryWithItsHighestScoringItem() {
        DigestLayout layout = DigestLayout.of(
                List.of(item("quiet angle", "https://b", "Reddit/r/java", "Java/JVM", "OPINION", 6, "valhalla"),
                        item("the news", "https://a", "OpenJDK JEP", "Java/JVM", "FEATURE", 9, "valhalla")),
                List.of());

        assertThat(all(layout).getFirst().lead().url()).isEqualTo("https://a");
    }

    @Test
    void rendersEveryStoryInExactlyOneSection() {
        // The whole point: a LAUNCH scoring 10 used to satisfy must-know, deals, critical trends and
        // top picks at once, and the reader met it four times before reaching anything new.
        DigestItem launch = item("Registry", "https://lab", "Claude Blog", "AI/LLM", "LAUNCH", 10, "mcp");
        DigestLayout layout = DigestLayout.of(List.of(launch), List.of(critical(launch)));

        assertThat(all(layout)).hasSize(1);
        assertThat(layout.criticalTrends()).hasSize(1);
        assertThat(layout.mustKnow()).isEmpty();
        assertThat(layout.deals()).isEmpty();
        assertThat(layout.topPicks()).isEmpty();
        assertThat(layout.signals()).isEmpty();
    }

    @Test
    void letsCriticalTrendsClaimAStoryBeforeMustKnow() {
        DigestItem item = item("Registry", "https://lab", "Claude Blog", "AI/LLM", "LAUNCH", 10, "mcp");

        assertThat(DigestLayout.of(List.of(item), List.of(critical(item))).criticalTrends())
                .extracting(story -> story.lead().url()).containsExactly("https://lab");
        assertThat(DigestLayout.of(List.of(item), List.of(critical(item))).mustKnow()).isEmpty();
    }

    @Test
    void fillsDealsAndToolsFromWhatMustKnowLeftBehind() {
        // A deal that clears the must-know bar belongs at the top, not repeated two sections later;
        // Deals & Tools is what you can adopt that you have NOT already read about.
        DigestLayout layout = DigestLayout.of(
                List.of(item("Big release", "https://a", "GitHub Releases", "Releases", "RELEASE", 9, "quarkus"),
                        item("Small release", "https://b", "CNCF Landscape", "Cloud/DevOps", "RELEASE", 6, "otel")),
                List.of());

        assertThat(layout.mustKnow()).extracting(story -> story.lead().url()).containsExactly("https://a");
        assertThat(layout.deals()).extracting(story -> story.lead().url()).containsExactly("https://b");
    }

    @Test
    void keepsItemsWithoutATopicKeyApartEvenWhenTheyShareACategory() {
        // correlationKey() falls back to the category so a degraded LLM response still correlates
        // something. Merging display entries on that fallback would hide unrelated stories instead.
        DigestLayout layout = DigestLayout.of(
                List.of(item("Story one", "https://a", "Hacker News", "AI/LLM", "DISCUSSION", 7, null),
                        item("Story two", "https://b", "Product Hunt", "AI/LLM", "LAUNCH", 7, null)),
                List.of());

        assertThat(all(layout)).hasSize(2);
    }

    @Test
    void overflowsPastTheMustKnowCapIntoTopPicks() {
        List<DigestItem> items = Stream.iterate(1, index -> index + 1).limit(6)
                .map(index -> item("Story " + index, "https://" + index, "GitHub", "AI/LLM",
                        "TREND", 9, "topic-" + index))
                .toList();

        DigestLayout layout = DigestLayout.of(items, List.of());

        assertThat(layout.mustKnow()).hasSize(5);
        assertThat(layout.topPicks()).extracting(story -> story.lead().url()).containsExactly("https://6");
    }

    @Test
    void dropsStoriesBelowTheSignalThreshold() {
        DigestLayout layout = DigestLayout.of(
                List.of(item("Long tail", "https://a", "Security Advisories", "Security/Privacy",
                        "INCIDENT", 3, "tail")),
                List.of());

        assertThat(all(layout)).isEmpty();
    }

    @Test
    void splitsTheTablesAtTheTopPickThreshold() {
        DigestLayout layout = DigestLayout.of(
                List.of(item("Eight", "https://a", "GitHub", "AI/LLM", "TREND", 8, "a"),
                        item("Seven", "https://b", "GitHub", "AI/LLM", "TREND", 7, "b"),
                        item("Six", "https://c", "GitHub", "AI/LLM", "TREND", 6, "c")),
                List.of());

        // "Seven" clears the must-know bar, so only the six lands in the lower table.
        assertThat(layout.mustKnow()).extracting(story -> story.lead().url())
                .containsExactly("https://a", "https://b");
        assertThat(layout.topPicks()).isEmpty();
        assertThat(layout.signals()).extracting(story -> story.lead().url()).containsExactly("https://c");
    }

    @Test
    void carriesTheLeadsSignalSoSectionsCanExplainTheScore() {
        DigestItem lead = item("Registry", "https://lab", "Claude Blog", "AI/LLM", "LAUNCH", 10, "mcp");
        DigestItem echo = item("repo", "https://gh", "GitHub", "AI/LLM", "TREND", 8, "mcp");

        DigestLayout layout = DigestLayout.of(List.of(lead, echo), List.of(critical(lead), critical(echo)));

        assertThat(layout.criticalTrends().getFirst().signal().item().url()).isEqualTo("https://lab");
    }

    @Test
    void keepsOneLinkPerCorroboratingSourceSoCollapsingLosesNothing() {
        // The reader meets the story once, but must still be able to reach the coverage that
        // confirmed it — otherwise de-duplicating the mail quietly deletes two thirds of its links.
        DigestLayout layout = DigestLayout.of(
                List.of(item("Registry launched", "https://lab", "Claude Blog", "AI/LLM", "LAUNCH", 9, "mcp"),
                        item("terse take", "https://hn-low", "Hacker News", "AI/LLM", "DISCUSSION", 6, "mcp"),
                        item("the thread", "https://hn-high", "Hacker News", "AI/LLM", "DISCUSSION", 8, "mcp")),
                List.of());

        assertThat(all(layout).getFirst().corroborations())
                .extracting(DigestItem::url)
                .containsExactly("https://lab", "https://hn-high");
    }

    @Test
    void reportsEveryRenderedUrlIncludingTheMembersFoldedIntoAStory() {
        // The weekly recap filters against this set, and it names items by their own URL — including
        // the ones that lost the lead. Reporting only the leads would let a folded member reappear.
        DigestLayout layout = DigestLayout.of(
                List.of(item("Registry", "https://lead", "Claude Blog", "AI/LLM", "LAUNCH", 9, "mcp"),
                        item("registry repo", "https://member", "GitHub", "AI/LLM", "TREND", 8, "mcp"),
                        item("Long tail", "https://dropped", "RSS/InfoQ", "Other", "OPINION", 3, "tail")),
                List.of());

        assertThat(layout.renderedUrls()).containsExactlyInAnyOrder("https://lead", "https://member");
    }

    private static List<DigestStory> all(DigestLayout layout) {
        return Stream.of(layout.criticalTrends(), layout.mustKnow(), layout.deals(),
                        layout.topPicks(), layout.signals())
                .flatMap(List::stream)
                .toList();
    }

    private static DigestItem item(String title, String url, String source, String category,
                                   String type, int score, String topicKey) {
        return new DigestItem(title, url, source, category, type, score, null, "summary", "why", topicKey);
    }

    private static Signal critical(DigestItem item) {
        return new Signal(item, SignalRank.CRITICAL, 151, List.of(SourceDomain.LABS, SourceDomain.CODE));
    }
}
