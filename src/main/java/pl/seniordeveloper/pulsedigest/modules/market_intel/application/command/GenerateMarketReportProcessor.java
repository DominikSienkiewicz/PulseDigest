package pl.seniordeveloper.pulsedigest.modules.market_intel.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketResearchService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EmailDeliveryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.LlmSynthesisPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportEnrichmentPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.AsyncConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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

    @Async(AsyncConfig.REPORT_EXECUTOR)
    public void process(String jobId) {
        log.info("=== [{}] Starting Market Intelligence Pipeline ===", jobId);
        Instant start = Instant.now();

        Optional<ReportJob> maybeJob = jobTracker.getJob(jobId);
        if (maybeJob.isEmpty()) {
            log.error("[{}] Job not found in tracker — aborting", jobId);
            return;
        }

        ReportJob job = maybeJob.get();
        jobTracker.track(job.inProgress());

        try {
            ResearchResult research = researchService.fetchAndFilter();
            log.info("[{}] Research completed: {}", jobId, research.summary());

            if (research.isEmpty()) {
                log.warn("[{}] All data sources returned empty results — skipping synthesis", jobId);
                jobTracker.track(job.error("All data sources returned empty results"));
                return;
            }

            ReportData report = synthesisPort.synthesize(research);
            ReportData cleaned = report.withCanonicalizedUrls();

            ReportData enriched = enrichmentPort.map(p -> p.enrich(cleaned)).orElse(cleaned);
            if (enriched != cleaned) {
                int trendCount = enriched.trends() != null ? enriched.trends().size() : 0;
                log.info("[{}] Report enriched with {} trend cluster(s)", jobId, trendCount);
            }

            storagePort.save(new PersistedReport(
                    enriched, jobId, Instant.now(),
                    research.tweets().size(), research.hackerNewsPosts().size(), research.githubRepos().size()
            ));

            jobTracker.track(job.done(enriched));
            log.info("=== [{}] Report generated successfully in {}s ===",
                    jobId, Duration.between(start, Instant.now()).getSeconds());

            emailPort.send(enriched, research);
            log.info("[{}] Email delivery triggered", jobId);

        } catch (Exception e) {
            log.error("[{}] Error during report generation: {}", jobId, e.getMessage(), e);
            jobTracker.track(job.error(e.getMessage()));
        }
    }
}
