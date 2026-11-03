package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EditionDeliveryGuard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceBreadth;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.CalibrationService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.EolRadarService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReadingTimeService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SavedBacklogService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SelfCritiqueService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SemanticClusteringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.StackMatchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.ReaderProfileService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceYieldService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.TrendVelocityService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.WeeklyRecapService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceFloorPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendMemory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaSignals;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJobStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.CallForPapersPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.DependencyProfilePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionInputPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReleaseRadarPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the end-to-end market report pipeline synchronously: research → synthesis → scoring →
 * persistence → email delivery. Each terminal outcome (DELIVERED / EMAIL_FAILED / ERROR)
 * is recorded in the job tracker so the caller can map it to a process exit code.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GenerateMarketReportProcessor {

    private final MarketIntelJobTracker jobTracker;
    private final MarketResearchService researchService;
    private final LlmSynthesisPort synthesisPort;
    private final ReportStoragePort storagePort;
    private final EmailDeliveryPort emailPort;
    private final EditionArchivePort editionArchivePort;
    private final RunSummaryPort runSummaryPort;
    private final EditionInputPort editionInputPort;
    private final DependencyProfilePort dependencyProfilePort;
    private final ReleaseRadarPort releaseRadarPort;
    private final StackMatchService stackMatchService;
    private final CalibrationService calibrationService;
    private final EolRadarService eolRadarService;
    private final SavedBacklogService savedBacklogService;
    private final ReadingTimeService readingTimeService;
    private final SelfCritiqueService selfCritiqueService;
    private final SemanticClusteringService semanticClusteringService;
    private final CallForPapersPort callForPapersPort;
    private final SavedBacklogPolicy savedBacklogPolicy;
    private final EditionDeliveryGuard editionDeliveryGuard;
    private final SignalScoringService signalScoringService;
    private final FeedbackPort feedbackPort;
    private final FeedbackNudgePolicy feedbackNudgePolicy;
    private final ReportHistoryPort reportHistoryPort;
    private final WeeklyRecapService weeklyRecapService;
    private final SourceYieldService sourceYieldService;
    private final TrendVelocityService trendVelocityService;
    private final ReaderProfileService readerProfileService;
    private final ReportHistoryPolicy reportHistoryPolicy;
    private final SourceFloorPolicy sourceFloorPolicy;

    // Guarded reads of the feedback store: "ask the policy, then read, else nothing", four times
    // over. Disabled yields an empty answer rather than a null, so no caller downstream needs to
    // know the policy exists — and process() carries four fewer branches than it used to.
    private Map<String, Integer> netVotesBySource() {
        return feedbackNudgePolicy.enabled() ? feedbackPort.netVotesBySource(feedbackNudgePolicy.lookbackDays()) : Map.of();
    }

    private Map<String, Integer> netVotesByCategory() {
        return feedbackNudgePolicy.enabled() ? feedbackPort.netVotesByCategory(feedbackNudgePolicy.lookbackDays()) : Map.of();
    }

    private java.util.Set<String> upvotedUrls() {
        return feedbackNudgePolicy.enabled() ? feedbackPort.upvotedUrls(feedbackNudgePolicy.lookbackDays()) : java.util.Set.of();
    }

    private List<SavedItem> savedItems() {
        return savedBacklogPolicy.enabled() ? feedbackPort.savedItems(savedBacklogPolicy.lookbackDays()) : List.of();
    }

    public void process(String jobId) {
        log.info("=== [{}] Starting Market Intelligence Pipeline ===", jobId);
        Instant start = Instant.now();

        Optional<ReportJob> maybeJob = jobTracker.getJob(jobId);
        if (maybeJob.isEmpty()) {
            log.error("[{}] Job not found in tracker — aborting", jobId);
            return;
        }

        ReportJob job = maybeJob.get().inProgress();
        jobTracker.track(job);

        LocalDate edition = LocalDate.now(ZoneOffset.UTC);
        // F16, cheap half: before a single source is fetched or a single token spent. Advisory —
        // two runs starting together both pass it — but it is what stops the common duplicate (a
        // manual dispatch after a successful scheduled run) from costing anything at all.
        if (editionDeliveryGuard.alreadyDelivered(jobId, edition)) {
            jobTracker.track(job.error("Edition " + edition + " has already been delivered"));
            return;
        }

        ResearchResult research = null;
        try {
            research = researchService.fetchAndFilter();
            log.info("[{}] Research completed: {}", jobId, research.summary());
            SourceBreadth.warnOnDegradedSources(jobId, research);

            int attempted = SourceBreadth.attempted(research);
            int active = SourceBreadth.active(research);

            if (research.isEmpty()) {
                log.warn("[{}] All data sources returned empty results — skipping synthesis", jobId);
                jobTracker.track(job.error("All data sources returned empty results"));
                sendFailureNotification(jobId, research, null,
                        SourceBreadth.shortfallDetail(research, "Pusty wynik", active, attempted));
                return;
            }

            // F10. Breadth, checked after the fetch and before the first token is spent. The
            // emptiness check above only catches a run where nothing at all came back, so a run that
            // lost eighteen of its nineteen sources used to ship anyway: two items, a critical marker
            // earned by those two, and a footer admitting a single source — a stub in a full
            // edition's clothes. Aborting here costs the fetch that already happened and nothing more.
            if (sourceFloorPolicy.isTooNarrow(active, attempted)) {
                int floor = sourceFloorPolicy.effectiveFloor(attempted);
                log.warn("[{}] Only {}/{} sources returned anything (floor {}) — skipping synthesis",
                        jobId, active, attempted, floor);
                jobTracker.track(job.error("Only " + active + " of " + attempted
                        + " sources returned anything, below the floor of " + floor));
                sendFailureNotification(jobId, research, null, SourceBreadth.shortfallDetail(research,
                        "Zbyt wąskie wydanie (próg: %d aktywnych źródeł)".formatted(floor),
                        active, attempted));
                return;
            }

            // N60. The floor above reads the fetch ledger, which counts each source's items before the
            // relevance, like-count and recency filters run — so a run where every source answered and
            // filtering left almost nothing clears it. This is the same question asked of what survived.
            // Not a quiet week: that is N03's, decided after synthesis on stories above the bar. A real
            // edition fills sixteen of sixteen sections, so three empty-but-healthy sections mean a
            // filter or a lookback window broke.
            int contributing = research.activeSourceCount();
            if (sourceFloorPolicy.isTooThin(contributing, active)) {
                log.warn("[{}] Only {} section(s) survived filtering (floor {}) — skipping synthesis",
                        jobId, contributing, sourceFloorPolicy.minContributingSections());
                jobTracker.track(job.error("Only " + contributing + " section(s) carried an item after "
                        + "filtering, below the floor of " + sourceFloorPolicy.minContributingSections()));
                sendFailureNotification(jobId, research, null,
                        "Po filtrach treść została w %d z %d odpytanych źródeł — próg to %d sekcji"
                                .formatted(contributing, attempted, sourceFloorPolicy.minContributingSections()));
                return;
            }

            Map<String, Integer> netVotesBySource = netVotesBySource();
            Map<String, Integer> netVotesByCategory = netVotesByCategory();

            // Re-distil the reader model BEFORE synthesis, so this run's prompt already carries it.
            Optional<ReaderProfile> readerProfile = readerProfileService.refresh(
                    LocalDate.now(ZoneOffset.UTC),
                    new ProfileEvidence(netVotesByCategory, netVotesBySource, List.of()));

            ReportData report = synthesisPort.synthesize(research);
            ReportData cleaned = report.withCanonicalizedUrls();

            // N23. Before scoring, so a cut item never reaches the scorer — and in shadow mode by
            // default, so for now this only writes the log the decision to enforce will be made on.
            cleaned = cleaned.withItems(selfCritiqueService.apply(cleaned.items()));

            // N21. Before scoring, because clustering only assigns correlation keys and everything
            // that counts sources and domains reads them. After the critique, so a cut item is never
            // embedded — the one call this feature makes is per edition, not per item.
            cleaned = cleaned.withItems(semanticClusteringService.cluster(cleaned.items()));

            List<PastEdition> history = readHistory();
            List<Signal> signals = signalScoringService.score(
                    cleaned.items() != null ? cleaned.items() : List.of(), netVotesBySource, netVotesByCategory);
            signals = TrendMemory.annotate(signals, history);
            signals = trendVelocityService.annotate(signals, history);
            ReportData finalReport = cleaned.withSignals(signals)
                    .withRadarAccuracy(trendVelocityService.accuracy(history))
                    .withReaderProfile(readerProfile.orElse(null));
            finalReport = weeklyRecapService
                    .assemble(LocalDate.now(ZoneOffset.UTC), signals, history)
                    .map(finalReport::withWeeklyRecap)
                    .orElse(finalReport);
            // One profile read, two features: the radar asks a registry about it (N07) and the stack
            // matcher compares advisories against it (N06). Reading it twice would let them disagree.
            var dependencyProfile = dependencyProfilePort.load();
            finalReport = finalReport
                    .withDependencyReleases(releaseRadarPort.outdated(dependencyProfile))
                    .withStackAffectingUrls(stackMatchService.advisoryUrlsAffectingStack(
                            dependencyProfile, research.securityAdvisories()));

            // Past editions only: this edition's own 🔴 items have had no chance to be voted on, and
            // counting them would drag every hit rate towards zero by construction.
            finalReport = finalReport.withCalibration(calibrationService.measure(history, upvotedUrls()));

            // The reader's own shelf, read from the same feedback rows the votes come from. History
            // is reused rather than re-queried: it is the only place a saved URL's headline survives.
            finalReport = finalReport.withSavedBacklog(
                    savedBacklogService.assemble(savedItems(), history));

            // N09. A deadline, not a report of a talk. Fetched here rather than in the research
            // fan-out because it is rendered deterministically and never goes near the prompt.
            finalReport = finalReport.withCallsForPapers(
                    callForPapersPort.openCalls(LocalDate.now(ZoneOffset.UTC)));

            // N04. Derived from what the edition already contains, so it costs no call and cannot
            // disagree with the items it describes.
            finalReport = finalReport.withReadingBudget(readingTimeService.estimate(finalReport.items()));

            // N27. Read from the same ledger the run log warns from, so the footer and the log can
            // never name different sources.
            finalReport = finalReport.withSilentSources(sourceYieldService.silentSources(history));

            // Not news and not scored: a date arrived. Attached after scoring precisely because it
            // must not compete with the stories for a slot (N08).
            finalReport = finalReport.withEolNotices(eolRadarService.due(LocalDate.now(ZoneOffset.UTC)));

            long criticalCount = signals.stream().filter(Signal::isCriticalTrend).count();
            log.info("[{}] Signal scoring: {} signals ({} CRITICAL)", jobId, signals.size(), criticalCount);

            job = job.generated(finalReport);
            jobTracker.track(job);

            sourceYieldService.logScoreboard(history);

            storagePort.save(new PersistedReport(
                    finalReport, jobId, Instant.now(),
                    research.tweets().size(), research.hackerNewsPosts().size(), research.githubRepos().size(),
                    research.sourceFetchReports()
            ));

            // After the report row exists: edition_inputs carries a foreign key to it, and the input is
            // only interesting for an edition that actually shipped.
            editionInputPort.save(jobId, research);

            job = job.persisted();
            jobTracker.track(job);
            log.info("=== [{}] Report generated and persisted in {}s ===",
                    jobId, Duration.between(start, Instant.now()).getSeconds());

            runSummaryPort.writeIntake(research,
                    finalReport.items() != null ? finalReport.items().size() : 0, (int) criticalCount);

            // Before the send, not after: if delivery fails, the edition this run paid to produce is
            // still on disk and still uploaded as a run artifact.
            editionArchivePort.archive(finalReport, research);

            // F16, authoritative half: the last thing before the irreversible one. A conditional
            // insert on the edition date, so of two runs arriving together exactly one is told yes.
            if (!editionDeliveryGuard.claim(jobId, edition)) {
                log.error("[{}] Another run already owns edition {} — refusing to send it twice", jobId, edition);
                jobTracker.track(job.error("Edition " + edition + " is already claimed for delivery"));
                return;
            }

            job = job.delivering();
            jobTracker.track(job);
            EmailDeliveryReceipt receipt = emailPort.send(finalReport, research);
            job = job.delivered();
            jobTracker.track(job);
            editionDeliveryGuard.markDelivered(jobId, edition);
            log.info("[{}] Email delivered via {}: {}", jobId, receipt.provider(), receipt.responseBody());

        } catch (Exception e) {
            log.error("[{}] Error during report generation: {}", jobId, e.getMessage(), e);
            if (job.status() == ReportJobStatus.DELIVERING) {
                // The email channel itself failed — the report is already persisted. Don't attempt to
                // notify through the same (broken) channel; the CI-level failure alert covers this case.
                jobTracker.track(job.emailFailed(e.getMessage()));
            } else {
                // Failure before delivery — the email channel is healthy, so notify unconditionally
                // instead of letting a broken run end silently.
                jobTracker.track(job.error(e.getMessage()));
                sendFailureNotification(jobId, research, e, null);
            }
        }
    }

    /**
     * Past editions, read once and reused for both trend memory and the Thursday recap. Runs before
     * the report is persisted, so "3rd edition in a row" never counts the edition being assembled.
     * A storage failure degrades to an amnesiac digest rather than losing the whole run.
     */
    private List<PastEdition> readHistory() {
        if (!reportHistoryPolicy.enabled()) {
            return List.of();
        }
        try {
            return reportHistoryPort.recentEditions(reportHistoryPolicy.lookbackDays());
        } catch (Exception e) {
            log.warn("Report history unavailable — publishing without trend memory: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Sends a standalone failure-alert email when the digest could not be produced (before delivery).
     * Always fires on a pre-delivery failure so a broken run is never silent. When a quota/rate-limit
     * signature is detected — among the data sources or in the aborting cause (e.g. LLM credits
     * depleted) — the named accounts to top up are attached; otherwise the alert carries the failure
     * reason only. Never throws: a failed alert must not mask the original error.
     */
    private void sendFailureNotification(String jobId, ResearchResult research, Throwable cause, String reason) {
        Set<String> accounts = new LinkedHashSet<>();
        if (cause != null && QuotaSignals.matches(rootMessage(cause))) {
            accounts.add(ApiAccounts.LLM);
        }
        if (research != null) {
            for (SourceFetchReport report : research.sourceFetchReports()) {
                if (report.isQuotaExhausted()) {
                    accounts.add(ApiAccounts.label(report.sourceName()));
                }
            }
        }
        String detail = cause != null ? rootMessage(cause) : reason;
        try {
            QuotaAlert alert = new QuotaAlert(new ArrayList<>(accounts), detail);
            EmailDeliveryReceipt receipt = emailPort.sendQuotaAlert(alert);
            log.warn("[{}] Failure alert sent ({} account(s) to top up) via {}",
                    jobId, accounts.size(), receipt.provider());
        } catch (Exception alertFailure) {
            log.error("[{}] Failed to send failure alert: {}", jobId, alertFailure.getMessage());
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
