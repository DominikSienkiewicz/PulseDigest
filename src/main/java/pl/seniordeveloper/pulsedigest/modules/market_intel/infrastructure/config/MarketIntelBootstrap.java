package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;


/**
 * Configuration and initialization for Market Intelligence module.
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class MarketIntelBootstrap {

    private final MarketIntelJobTracker jobTracker;
    private final ReportStoragePort storagePort;

    @PostConstruct
    public void init() {
        log.info("Bootstrapping Market Intelligence module...");
        storagePort.getLatest().ifPresent(persisted -> {
            ReportJob job = ReportJob.done(persisted.jobId(), persisted.report(), persisted.generatedAt());
            jobTracker.track(job);
            log.info("Restored latest report from storage: jobId={}, date={}",
                    persisted.jobId(), persisted.generatedAt());
        });
    }
}
