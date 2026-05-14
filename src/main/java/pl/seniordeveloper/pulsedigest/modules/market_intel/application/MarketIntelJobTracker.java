package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory job tracker for market intelligence reporting.
 * <p>
 * Completed terminal jobs are evicted after 2 hours via a scheduled cleanup
 * that runs every 30 minutes, preventing unbounded map growth.
 * <p>
 * Jobs stuck in a non-terminal status for longer than JOB_TIMEOUT are
 * automatically marked as ERROR to prevent blocking future report generation.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MarketIntelJobTracker {

    private static final Duration TTL = Duration.ofHours(2);
    private static final Duration JOB_TIMEOUT = Duration.ofHours(1);

    private final ConcurrentHashMap<String, ReportJob> jobs = new ConcurrentHashMap<>();

    public void track(ReportJob job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<ReportJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public java.util.Collection<ReportJob> getAll() {
        return jobs.values();
    }

    @Scheduled(fixedDelay = 1800000)
    void evictExpiredJobs() {
        Instant timeoutCutoff = Instant.now().minus(JOB_TIMEOUT);
        jobs.entrySet().forEach(entry -> {
            ReportJob job = entry.getValue();
            if (job.status().isActive() && job.createdAt() != null && job.createdAt().isBefore(timeoutCutoff)) {
                log.warn("Job [{}] timed out (created: {}). Marking as ERROR.", job.jobId(), job.createdAt());
                jobs.put(entry.getKey(), job.error("Job timed out after 1 hour — likely crashed during processing"));
            }
        });

        Instant cutoff = Instant.now().minus(TTL);
        jobs.values().removeIf(job ->
                job.status().isTerminal() && job.completedAt() != null
                        && job.completedAt().isBefore(cutoff)
        );
    }
}
