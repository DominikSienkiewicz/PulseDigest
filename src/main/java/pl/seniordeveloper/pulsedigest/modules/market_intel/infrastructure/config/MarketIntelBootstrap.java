package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.MarketIntelJobTracker;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RateLimitPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportJob;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReaderProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportHistoryProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportRuntimeProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;

import java.time.Duration;


/**
 * Configuration and initialization for Market Intelligence module.
 *
 * <p>Translates infrastructure-bound {@code @ConfigurationProperties} into narrow,
 * application-owned policy value objects so application services do not import infrastructure
 * configuration types directly (see ArchUnit rule
 * {@code APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE_CONFIG}).
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
        restoreLatestReport();
    }

    /**
     * Loads the previous edition into the job tracker on a best-effort basis.
     *
     * <p>The restored edition only seeds the rate-limit cooldown and the "latest report" query; it is
     * never a precondition for generating today's digest. An archive row this build cannot read must
     * therefore not abort the run — a single unreadable payload would otherwise wedge every future
     * run until someone deleted it by hand. The failure is logged at WARN because losing the restore
     * does have a consequence: the cooldown starts from an empty tracker, so a second run started
     * within the cooldown window is no longer suppressed.
     */
    private void restoreLatestReport() {
        try {
            storagePort.getLatest().ifPresent(persisted -> {
                ReportJob job = ReportJob.done(persisted.jobId(), persisted.report(), persisted.generatedAt());
                jobTracker.track(job);
                log.info("Restored latest report from storage: jobId={}, date={}",
                        persisted.jobId(), persisted.generatedAt());
            });
        } catch (RuntimeException e) {
            log.warn("Could not restore the latest report from storage — continuing without it. "
                    + "The generation cooldown will be evaluated against an empty tracker.", e);
        }
    }

    @Bean
    ResearchPolicy researchPolicy(ResearchProperties research, InterestProfileProperties profile) {
        return new ResearchPolicy(research.minLikes(), research.daysBack(),
                research.authorityUsernames(), profile.relevanceKeywords());
    }

    @Bean
    RateLimitPolicy reportRateLimitPolicy(ReportRuntimeProperties runtime) {
        int cooldownMinutes = Math.max(0, runtime.minGenerationIntervalMinutes());
        return new RateLimitPolicy(Duration.ofMinutes(cooldownMinutes));
    }

    @Bean
    FeedbackNudgePolicy feedbackNudgePolicy(FeedbackProperties feedback) {
        return new FeedbackNudgePolicy(feedback.enabled(), feedback.lookbackDays());
    }

    @Bean
    ReportHistoryPolicy reportHistoryPolicy(ReportHistoryProperties history) {
        return new ReportHistoryPolicy(history.enabled(), history.lookbackDays());
    }

    @Bean
    ReaderProfilePolicy readerProfilePolicy(ReaderProfileProperties profile) {
        return new ReaderProfilePolicy(profile.enabled(), profile.minVotes(),
                profile.refreshDays(), profile.hypothesisTtlDays());
    }
}
