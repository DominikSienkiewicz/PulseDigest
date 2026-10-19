package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory job tracker for the one job a run produces.
 * <p>
 * It holds no eviction and no timeout: the process generates a single digest and exits within
 * minutes, so the map never grows and an entry never outlives the JVM that wrote it. It carried
 * both until 2026-10-17, as a {@code @Scheduled} cleanup on a 30-minute delay with a 2-hour TTL
 * and a 1-hour stuck-job timeout — none of which could fire before the process was gone. Keeping
 * them would mean keeping a lifecycle for state that has no lifetime.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MarketIntelJobTracker {

    private final ConcurrentHashMap<String, ReportJob> jobs = new ConcurrentHashMap<>();

    public void track(ReportJob job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<ReportJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
