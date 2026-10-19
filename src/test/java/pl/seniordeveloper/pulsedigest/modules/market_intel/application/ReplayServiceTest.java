package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PromptCostEstimatePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayServiceTest {

    private final ReportHistoryPort history = mock(ReportHistoryPort.class);
    private final EditionInputPort inputs = mock(EditionInputPort.class);
    private final LlmSynthesisPort synthesis = mock(LlmSynthesisPort.class);
    private final PromptCostEstimatePort estimates = mock(PromptCostEstimatePort.class);
    private final ReplayService service =
            new ReplayService(history, new SignalScoringService(ScoringPolicy.defaults()), inputs, synthesis, estimates);

    @Test
    void anEditionScoredByTodaysRubricUnchangedReplaysAsIdentical() {
        // Stored exactly as the current rubric scores it: one Hacker News item, no corroboration.
        DigestItem item = item("https://news.ycombinator.com/item?id=1", "Loom in production", "Hacker News");
        when(
                history.edition("job-1")).thenReturn(Optional.of(TestModels.pastEdition(Instant.now(), List.of(signal(
                        item, SignalRank.MODERATE, 80)))));

        ReplayService.ReplayReport report = service.replay("job-1").orElseThrow();

        assertThat(report.itemsReplayed()).isEqualTo(1);
        assertThat(report.isIdentical())
                .as("same items, same rubric — a replay must not invent differences")
                .isTrue();
    }

    /**
     * The point of the exercise: an edition stored with a rank today's rubric no longer gives is
     * reported, item by item. This is what turns "we changed the scoring, did it help?" from an
     * opinion into a diff.
     */
    @Test
    void anEditionWhoseStoredRankTodaysRubricWouldNotGiveIsReportedAsChanged() {
        DigestItem item = item("https://news.ycombinator.com/item?id=2", "A popular blog post", "Hacker News");
        when(
                history.edition("job-2")).thenReturn(Optional.of(TestModels.pastEdition(Instant.now(), List.of(signal(
                        item, SignalRank.CRITICAL, 140)))));

        ReplayService.ReplayReport report = service.replay("job-2").orElseThrow();

        assertThat(report.isIdentical()).isFalse();
        assertThat(report.changes()).singleElement().satisfies(change -> {
            assertThat(change.wasRank()).isEqualTo("CRITICAL");
            assertThat(change.nowRank())
                    .as("a single source is no longer CRITICAL since F2")
                    .isEqualTo("MODERATE");
            assertThat(change.url()).isEqualTo("https://news.ycombinator.com/item?id=2");
        });
    }

    @Test
    void anUnknownJobIdReplaysNothingRatherThanAnEmptyReport() {
        when(history.edition("nope")).thenReturn(Optional.empty());

        assertThat(service.replay("nope")).isEmpty();
    }

    @Test
    void replayNeverTouchesTheHistoryWindow() {
        when(history.edition("job-3")).thenReturn(Optional.empty());

        service.replay("job-3");

        org.mockito.Mockito.verify(history, org.mockito.Mockito.never()).recentEditions(anyInt());
    }

    /**
     * The diff names the source of every item that vanished, because "which source dropped out" is
     * exactly the question two bare lists leave the reader to work out.
     */
    @org.junit.jupiter.api.Test
    void aPromptReplayNamesWhatAppearedAndWhatDisappearedWithItsSource() {
        DigestItem shipped = item("https://x/1", "Story that survived", "GitHub");
        DigestItem dropped = item("https://x/2", "Story the prompt no longer picks", "Reddit");
        DigestItem fresh = item("https://x/3", "Story today's prompt found", "arXiv");
        when(
                inputs.load("job-9")).thenReturn(Optional.of(org.instancio.Instancio.create(
                        pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult.class)));
        when(
                history.edition("job-9")).thenReturn(Optional.of(TestModels.pastEdition(Instant.now(), List.of(signal(
                        shipped, SignalRank.MODERATE, 80), signal(dropped, SignalRank.MODERATE, 70)))));
        when(estimates.estimate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model
                        .PromptCostEstimate.unpriced(12_000));
        when(
                synthesis.synthesize(org.mockito.ArgumentMatchers.any())).thenReturn(new pl.seniordeveloper
                        .pulsedigest.modules.market_intel.domain.model.ReportData("p", "e", List.of(), List.of(shipped, fresh)));

        ReplayService.PromptReplayReport report = service.replayPrompt("job-9").orElseThrow();

        assertThat(report.isIdentical()).isFalse();
        assertThat(report.kept()).isEqualTo(1);
        assertThat(report.appeared()).containsExactly("arXiv | Story today's prompt found");
        assertThat(report.disappeared()).containsExactly("Reddit | Story the prompt no longer picks");
        assertThat(report.estimate().describe()).contains("12000").contains("unpriced");
    }

    /** An edition from before `edition_inputs` existed has nothing to re-run, and must say so. */
    @org.junit.jupiter.api.Test
    void anEditionWithoutAStoredInputCannotBePromptReplayedAndCostsNothing() {
        when(inputs.load("job-old")).thenReturn(Optional.empty());

        assertThat(service.replayPrompt("job-old")).isEmpty();
        org.mockito.Mockito.verify(synthesis, org.mockito.Mockito.never())
                .synthesize(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(estimates, org.mockito.Mockito.never())
                .estimate(org.mockito.ArgumentMatchers.any());
    }

    private static DigestItem item(String url, String title, String source) {
        return TestModels.digestItem(title, url, source, "Java/JVM", "RELEASE", 7, 0, "summary", "why");
    }

    private static Signal signal(DigestItem item, SignalRank rank, int score) {
        return new Signal(item, rank, score, List.of(), null, null, null);
    }
}
