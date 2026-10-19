package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptCostEstimate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PromptCostEstimatePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Re-scores a past edition with today's rubric and reports what would have changed.
 *
 * <p>This is the half of N25 that costs nothing and is deterministic. The pipeline has two
 * independent stages a change can land in: the **prompt**, which decides which items exist, and the
 * **rubric** — {@code SignalScoringService}, the cross-source rule, the source weights, the
 * preference multiplier — which decides what they are worth. Replaying the rubric needs only the
 * items a past edition already published, so it runs offline, for free, against every edition in the
 * archive. Replaying the prompt needs a real model call over the stored input (N24) and is therefore
 * a separate, metered exercise.
 *
 * <p>Nothing is sent, nothing is written: a replay is a read and a report.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReplayService {

    private final ReportHistoryPort reportHistoryPort;
    private final SignalScoringService signalScoringService;
    private final EditionInputPort editionInputPort;
    private final LlmSynthesisPort synthesisPort;
    private final PromptCostEstimatePort costEstimatePort;

    /** One item's before/after, as the replay prints it. */
    public record RankChange(String url, String title, String wasRank, int wasScore,
                             String nowRank, int nowScore) {

        boolean changed() {
            return !wasRank.equals(nowRank) || wasScore != nowScore;
        }
    }

    /** The outcome of replaying one edition's rubric. */
    public record ReplayReport(String jobId, int itemsReplayed, List<RankChange> changes) {

        public boolean isIdentical() {
            return changes.isEmpty();
        }
    }

    /** What replaying the prompt produced, against what shipped. */
    public record PromptReplayReport(String jobId, PromptCostEstimate estimate,
                                     List<String> appeared, List<String> disappeared, int kept) {

        public boolean isIdentical() {
            return appeared.isEmpty() && disappeared.isEmpty();
        }
    }

    /**
     * Re-runs the current prompt over a stored edition's input and reports what the model would
     * produce differently today.
     *
     * <p><b>This spends money.</b> One synthesis call per invocation, which is why it is behind its
     * own flag and why the estimate is logged before the call rather than the bill after it. Unlike
     * the rubric replay it is also not deterministic: two runs of the same prompt over the same input
     * can differ, so a difference here is evidence to read rather than an assertion to trust.
     *
     * <p>Empty when the edition has no stored input — editions written before {@code edition_inputs}
     * existed cannot be replayed, and saying so beats replaying something else.
     */
    public Optional<PromptReplayReport> replayPrompt(String jobId) {
        Optional<ResearchResult> stored = editionInputPort.load(jobId);
        if (stored.isEmpty()) {
            log.warn("replay-prompt no stored input for edition {} — nothing to re-run", jobId);
            return Optional.empty();
        }
        Optional<PastEdition> shipped = reportHistoryPort.edition(jobId);
        if (shipped.isEmpty()) {
            log.warn("replay-prompt edition {} is not in the archive", jobId);
            return Optional.empty();
        }

        ResearchResult input = stored.get();
        PromptCostEstimate estimate = costEstimatePort.estimate(input);
        log.info("replay-prompt about to spend: {} — edition {}", estimate.describe(), jobId);

        ReportData replayed = synthesisPort.synthesize(input);
        return Optional.of(diff(jobId, estimate, shipped.get(), replayed));
    }

    private PromptReplayReport diff(String jobId, PromptCostEstimate estimate,
                                    PastEdition shipped, ReportData replayed) {
        Set<String> before = shipped.signals().stream()
                .map(signal -> signal.item().url()).collect(Collectors.toSet());
        List<DigestItem> after = replayed.items() != null ? replayed.items() : List.of();
        Set<String> afterUrls = after.stream().map(DigestItem::url).collect(Collectors.toSet());

        List<String> appeared = after.stream()
                .filter(item -> !before.contains(item.url()))
                .map(item -> "%s | %s".formatted(item.source(), item.title()))
                .toList();
        // Names the source, because "which source dropped out" is the question a diff of two lists
        // leaves the reader to work out for themselves.
        List<String> disappeared = shipped.signals().stream()
                .filter(signal -> !afterUrls.contains(signal.item().url()))
                .map(signal -> "%s | %s".formatted(signal.item().source(), signal.item().title()))
                .toList();
        int kept = (int) after.stream().filter(item -> before.contains(item.url())).count();
        return new PromptReplayReport(jobId, estimate, appeared, disappeared, kept);
    }

    /** Prints the prompt replay to the run log. */
    public void logPromptReplay(PromptReplayReport report) {
        log.info("=== Prompt replay of edition {} ({}) ===", report.jobId(), report.estimate().describe());
        if (report.isIdentical()) {
            log.info("replay-prompt result=identical kept={} — today's prompt selects the same items",
                    report.kept());
            return;
        }
        log.info("replay-prompt result=changed kept={} appeared={} disappeared={}",
                report.kept(), report.appeared().size(), report.disappeared().size());
        report.appeared().forEach(line -> log.info("replay-prompt + {}", line));
        report.disappeared().forEach(line -> log.info("replay-prompt - {}", line));
    }

    /** Replays the edition, or empty when the archive has no such job id. */
    public Optional<ReplayReport> replay(String jobId) {
        return reportHistoryPort.edition(jobId).map(edition -> report(jobId, edition));
    }

    private ReplayReport report(String jobId, PastEdition edition) {
        List<Signal> shipped = edition.signals();
        Map<String, Signal> rescored = signalScoringService
                .score(shipped.stream().map(Signal::item).toList())
                .stream()
                .collect(Collectors.toMap(s -> s.item().url(), Function.identity(), (a, b) -> a));

        List<RankChange> changes = new ArrayList<>();
        for (Signal before : shipped) {
            Signal after = rescored.get(before.item().url());
            if (after == null) {
                continue;
            }
            RankChange change = new RankChange(before.item().url(), before.item().title(),
                    before.rank().name(), before.signalScore(),
                    after.rank().name(), after.signalScore());
            if (change.changed()) {
                changes.add(change);
            }
        }
        return new ReplayReport(jobId, shipped.size(), List.copyOf(changes));
    }

    /**
     * Prints the replay to the run log. Deliberately a log dump rather than a file: a replay is run
     * by hand, read once, and acted on — persisting it would be a second archive to keep honest.
     */
    public void logReplay(ReplayReport report) {
        log.info("=== Replay of edition {} — {} item(s) re-scored with today's rubric ===",
                report.jobId(), report.itemsReplayed());
        if (report.isIdentical()) {
            log.info("replay result=identical — today's rubric ranks this edition exactly as it shipped");
            return;
        }
        log.info("replay result=changed items={}", report.changes().size());
        report.changes().forEach(c -> log.info("replay {} {} -> {} {} | {} | {}",
                c.wasRank(), c.wasScore(), c.nowRank(), c.nowScore(), c.title(), c.url()));
    }
}
