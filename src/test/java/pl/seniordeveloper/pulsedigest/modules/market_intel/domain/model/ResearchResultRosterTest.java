package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.testing.ResearchResults;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1. The roster inside {@link ResearchResult} has to list every source the record carries, or the
 * five aggregations that read it quietly disagree with the data.
 *
 * <p>This is the failure the roster exists to prevent, stated as a test rather than as a convention:
 * before it, a new source was added to {@code isEmpty}, {@code activeSourceCount}, {@code allTitles},
 * {@code rawTotalCount} and {@code summary} by hand, and one that reached four of the five compiled
 * perfectly and reported itself wrong — a watchlist that says "0 wzmianek" because the source was
 * never scanned looks exactly like a quiet week.
 */
class ResearchResultRosterTest {

    /** Not a source: fetch reports describe the attempt, not what any source returned. */
    private static final Set<String> NOT_A_SOURCE = Set.of("sourceFetchReports");

    @Test
    void everySourceListOnTheRecordIsRegisteredInTheRoster() {
        List<String> sourceComponents = List.of(ResearchResult.class.getRecordComponents()).stream()
                .filter(c -> c.getType() == List.class)
                .map(RecordComponent::getName)
                .filter(name -> !NOT_A_SOURCE.contains(name))
                .toList();

        ResearchResult result = ResearchResults.empty();

        assertThat(result.activeSourceCount())
                .as("an empty result has no active source")
                .isZero();
        assertThat(sourceComponents)
                .as("source lists on the record")
                .hasSize(16);
        // The counts are everything before the em dash; the tail carries the total and the
        // timestamp, and has a comma of its own.
        String counts = result.summary().split(" \u2014 ")[0];

        assertThat(counts.split(", ").length)
                .as("summary prints one entry per registered source — if this drifts from the "
                        + "component count above, a source is on the record but not in sections()")
                .isEqualTo(sourceComponents.size());
    }

    @Test
    void aSourceThatReturnedSomethingCountsAsActiveAndIsScanned() {
        ResearchResult result = ResearchResults.builder()
                .hackerNews(List.of(new HackerNewsPost("Loom lands", "https://news.example/1", 10)))
                .build();

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.activeSourceCount()).isEqualTo(1);
        assertThat(result.allTitles()).containsExactly("Loom lands");
    }

    @Test
    void theRawTotalCountsPagedSourcesByWhatTheyFetchedAndTheRestByWhatTheyKept() {
        // The distinction the roster encodes: five sources page and report a pre-filter count, the
        // other eleven return everything they have, so "raw" and "kept" are the same number.
        ResearchResult result = ResearchResults.builder()
                .hackerNews(List.of(new HackerNewsPost("Kept", "https://news.example/1", 10)))
                .papers(List.of(new ResearchPaper("2501.00001", "Paper", "abstract", List.of("A"),
                        "https://arxiv.example/1", "cs.SE", java.time.LocalDateTime.parse("2026-05-14T10:00:00"))))
                .rawCounts(RawCounts.builder().hackerNews(118).build())
                .build();

        assertThat(result.rawTotalCount())
                .as("118 fetched from HN plus the one paper arXiv returned")
                .isEqualTo(119);
    }
}
