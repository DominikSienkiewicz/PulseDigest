package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PublishedUrlsPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.PreScoringProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "PREFERENCJE CZYTELNIKA" prompt block (C13): what the reader has repeatedly asked for more or
 * less of, distilled from per-category 👍/👎 votes.
 */
class ReportPromptBuilderPreferencesTest {

    private static final String BLOCK = "PREFERENCJE CZYTELNIKA";

    private ReportPromptBuilder builder(boolean feedbackEnabled, Map<String, Integer> categoryVotes) {
        return new ReportPromptBuilder(new ObjectMapper(), noPublishedHistory(),
                new DedupProperties(true, 10), new InterestProfileProperties("Test Persona", List.of("java")),
                feedbackPort(categoryVotes), new FeedbackProperties(feedbackEnabled, 30, "", ""),
                candidates -> Map.of(), new PreScoringProperties(false, 50));
    }

    @Test
    void promptStatesTheReadersExpressedCategoryPreferences() {
        String prompt = builder(true, Map.of("java/jvm", 5, "research", -4)).buildUserPrompt(research());

        assertThat(prompt).contains(BLOCK);
        assertThat(prompt).contains("Chce więcej: java/jvm");
        assertThat(prompt).contains("Chce mniej: research");
    }

    @Test
    void theBlockIsWordedAsAPreferenceNotAFilter() {
        // A muted topic that genuinely matters must still be able to surface, or the preference loop
        // becomes a trap the category can never climb out of.
        String prompt = builder(true, Map.of("research", -4)).buildUserPrompt(research());

        assertThat(prompt).contains("NIE filtr");
    }

    @Test
    void aSingleStrayClickIsNoiseAndNeverReachesThePrompt() {
        assertThat(builder(true, Map.of("research", -2)).buildUserPrompt(research())).doesNotContain(BLOCK);
    }

    @Test
    void theBlockIsAbsentWhileTheReceiverStillSendsNoCategory() {
        assertThat(builder(true, Map.of()).buildUserPrompt(research())).doesNotContain(BLOCK);
    }

    @Test
    void theBlockIsAbsentWhenFeedbackIsDisabled() {
        assertThat(builder(false, Map.of("java/jvm", 5)).buildUserPrompt(research())).doesNotContain(BLOCK);
    }

    private static ResearchResult research() {
        return new ResearchResult(
                List.of(), List.of(new HackerNewsPost("HN item", "https://news.example/hn", 120)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                LocalDateTime.parse("2026-05-14T10:00:00"), 0, 1, 0, 0, 0);
    }

    private static PublishedUrlsPort noPublishedHistory() {
        return new PublishedUrlsPort() {
            @Override
            public Set<String> recentlyPublishedUrls(int lookbackDays) {
                return Set.of();
            }

            @Override
            public List<String> recentlyPublishedTitles(int lookbackDays, int maxTitles) {
                return List.of();
            }
        };
    }

    private static FeedbackPort feedbackPort(Map<String, Integer> categoryVotes) {
        return new FeedbackPort() {
            @Override
            public Set<String> downvotedUrls(int lookbackDays) {
                return Set.of();
            }

            @Override
            public Map<String, Integer> netVotesBySource(int lookbackDays) {
                return Map.of();
            }

            @Override
            public Map<String, Integer> netVotesByCategory(int lookbackDays) {
                return categoryVotes;
            }
        };
    }
}
