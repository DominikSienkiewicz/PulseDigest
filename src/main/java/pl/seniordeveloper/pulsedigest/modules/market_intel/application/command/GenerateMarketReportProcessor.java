package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ApiAccounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EmailDeliveryReceipt;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaSignals;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportEnrichmentPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.TechDemandNarratorPort;
import pl.seniordeveloper.pulsedigest.shared.async.AsyncQualifiers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Asynchronous processor for generating market reports.
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
    private final Optional<ReportEnrichmentPort> enrichmentPort;
    private final SignalScoringService signalScoringService;
    private final TechDemandNarratorPort techDemandNarrator;

    @Async(AsyncQualifiers.REPORT_EXECUTOR)
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

            if (research.isEmpty()) {
                log.warn("[{}] All data sources returned empty results — skipping synthesis", jobId);
                maybeSendQuotaAlert(jobId, research, null);
                jobTracker.track(job.error("All data sources returned empty results"));
                return;
            }

            research = narrateTechDemand(research);

            ReportData report = synthesisPort.synthesize(research);
            ReportData cleaned = report.withCanonicalizedUrls();

            ReportData enriched = enrichmentPort.map(p -> p.enrich(cleaned)).orElse(cleaned);
            if (enriched != cleaned) {
                int trendCount = enriched.trends() != null ? enriched.trends().size() : 0;
                log.info("[{}] Report enriched with {} trend cluster(s)", jobId, trendCount);
            }

            List<Signal> signals = signalScoringService.score(enriched.items() != null ? enriched.items() : List.of());
            ReportData finalReport = enriched.withSignals(signals);
            long criticalCount = signals.stream().filter(Signal::isCriticalTrend).count();
            log.info("[{}] Signal scoring: {} signals ({} CRITICAL)", jobId, signals.size(), criticalCount);

            job = job.generated(finalReport);
            jobTracker.track(job);

            storagePort.save(new PersistedReport(
                    finalReport, jobId, Instant.now(),
                    research.tweets().size(), research.hackerNewsPosts().size(), research.githubRepos().size()
            ));

            job = job.persisted();
            jobTracker.track(job);
            log.info("=== [{}] Report generated and persisted in {}s ===",
                    jobId, Duration.between(start, Instant.now()).getSeconds());

            job = job.delivering();
            jobTracker.track(job);
            EmailDeliveryReceipt receipt = emailPort.send(finalReport, research);
            jobTracker.track(job.delivered());
            log.info("[{}] Email delivered via {}: {}", jobId, receipt.provider(), receipt.responseBody());

        } catch (Exception e) {
            log.error("[{}] Error during report generation: {}", jobId, e.getMessage(), e);
            maybeSendQuotaAlert(jobId, research, e);
            if (job.status().name().startsWith("DELIVER")) {
                jobTracker.track(job.emailFailed(e.getMessage()));
            } else {
                jobTracker.track(job.error(e.getMessage()));
            }
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
     * When the digest could not be produced, sends a standalone alert naming the accounts to top up.
     * Fires only if a quota/rate-limit signature is present — either among the data sources or in the
     * failure that aborted synthesis (e.g. LLM credits depleted). Never throws: a failed alert must
     * not mask the original error.
     */
    private void maybeSendQuotaAlert(String jobId, ResearchResult research, Throwable cause) {
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
        if (accounts.isEmpty()) {
            return;
        }
        try {
            QuotaAlert alert = new QuotaAlert(new ArrayList<>(accounts), cause != null ? rootMessage(cause) : null);
            EmailDeliveryReceipt receipt = emailPort.sendQuotaAlert(alert);
            log.warn("[{}] Quota alert sent for {} account(s) via {}", jobId, accounts.size(), receipt.provider());
        } catch (Exception alertFailure) {
            log.error("[{}] Failed to send quota alert: {}", jobId, alertFailure.getMessage());
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
