package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SourceYieldService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.TrendVelocityService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.WeeklyRecapService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.FeedbackPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandNarratorPort;

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
import java.util.stream.Collectors;

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
    private final SignalScoringService signalScoringService;
    private final TechDemandNarratorPort techDemandNarrator;
    private final FeedbackPort feedbackPort;
    private final FeedbackNudgePolicy feedbackNudgePolicy;
    private final ReportHistoryPort reportHistoryPort;
    private final WeeklyRecapService weeklyRecapService;
    private final SourceYieldService sourceYieldService;
    private final TrendVelocityService trendVelocityService;
    private final ReportHistoryPolicy reportHistoryPolicy;

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

        ResearchResult research = null;
        try {
            research = researchService.fetchAndFilter();
            log.info("[{}] Research completed: {}", jobId, research.summary());
            warnOnDegradedSources(jobId, research);

            if (research.isEmpty()) {
                log.warn("[{}] All data sources returned empty results — skipping synthesis", jobId);
                jobTracker.track(job.error("All data sources returned empty results"));
                sendFailureNotification(jobId, research, null,
                        "Wszystkie źródła danych zwróciły pusty wynik w oknie 24h");
                return;
            }

            research = narrateTechDemand(research);

            ReportData report = synthesisPort.synthesize(research);
            ReportData cleaned = report.withCanonicalizedUrls();

            Map<String, Integer> netVotesBySource = feedbackNudgePolicy.enabled()
                    ? feedbackPort.netVotesBySource(feedbackNudgePolicy.lookbackDays())
                    : Map.of();
            List<PastEdition> history = readHistory();
            List<Signal> signals = signalScoringService.score(
                    cleaned.items() != null ? cleaned.items() : List.of(), netVotesBySource);
            signals = TrendMemory.annotate(signals, history);
            signals = trendVelocityService.annotate(signals, history);
            ReportData finalReport = cleaned.withSignals(signals)
                    .withRadarAccuracy(trendVelocityService.accuracy(history));
            finalReport = weeklyRecapService
                    .assemble(LocalDate.now(ZoneOffset.UTC), signals, history)
                    .map(finalReport::withWeeklyRecap)
                    .orElse(finalReport);
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

            job = job.persisted();
            jobTracker.track(job);
            log.info("=== [{}] Report generated and persisted in {}s ===",
                    jobId, Duration.between(start, Instant.now()).getSeconds());

            job = job.delivering();
            jobTracker.track(job);
            EmailDeliveryReceipt receipt = emailPort.send(finalReport, research);
            job = job.delivered();
            jobTracker.track(job);
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
     * Past editions, read once and reused for both trend memory and the Friday recap. Runs before
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
     * Attaches the one-sentence LLM interpretation to the tech-demand pulse, if present. Narrator
     * failures degrade gracefully (blank narrative) and never abort the pipeline.
     */
    private ResearchResult narrateTechDemand(ResearchResult research) {
        if (research.techDemand() == null) {
            return research;
        }
        String narrative = techDemandNarrator.narrate(research.techDemand());
        return research.withTechDemand(research.techDemand().withNarrative(narrative));
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

    /**
     * Logs a WARN when one or more sources failed this run, so a degraded digest (e.g. 15 of 18
     * sources down but one returned data) is visible in the logs rather than shipping silently as if
     * the run had been healthy.
     */
    private void warnOnDegradedSources(String jobId, ResearchResult research) {
        List<SourceFetchReport> failed = research.sourceFetchReports().stream()
                .filter(r -> r.status() == SourceFetchStatus.FAILED)
                .toList();
        if (failed.isEmpty()) {
            return;
        }
        log.warn("[{}] Degraded run: {}/{} sources failed — {}", jobId, failed.size(),
                research.sourceFetchReports().size(),
                failed.stream().map(SourceFetchReport::sourceName).collect(Collectors.joining(", ")));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
