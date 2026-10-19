package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.EolPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.DeliveryGuardPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.FeedbackNudgePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.RetrospectivePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SelfCritiquePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SemanticClusteringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceDisablePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReportHistoryPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SourceFloorPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EolProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DeliveryProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RetrospectiveProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SavedProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SectionsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SelfCritiqueProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SemanticClusteringProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SourcesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReaderProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportHistoryProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ScoringProperties;

/**
 * Configuration and initialization for Market Intelligence module.
 *
 * <p>Translates infrastructure-bound {@code @ConfigurationProperties} into narrow,
 * application-owned policy value objects so application services do not import infrastructure
 * configuration types directly (see ArchUnit rule
 * {@code APPLICATION_DOES_NOT_DEPEND_ON_INFRASTRUCTURE_CONFIG}).
 *
 * <p>It holds no state and no startup step. Until 2026-10-18 it also restored the previous edition
 * into the job tracker from a {@code @PostConstruct} — a cache warm-up whose only reader was
 * {@code GetLatestMarketReportService}, itself never called by anything. Both are gone; what is
 * left is the translation layer the class is named for.
 */
@Configuration
public class MarketIntelBootstrap {

    @Bean
    ResearchPolicy researchPolicy(ResearchProperties research, InterestProfileProperties profile) {
        return new ResearchPolicy(research.minLikes(), research.daysBack(),
                research.authorityUsernames(), profile.relevanceKeywords());
    }

    /**
     * F10 and N60. Read straight from the properties rather than added to {@link ResearchProperties}: that
     * record is the tweet filter's contract — {@code TwitterSearchAdapter} binds it for exactly that
     * — and how many sources an edition needs is not a tweet-filtering setting. The key still lives
     * in the {@code report.research} family in the yaml, where a reader looks for it.
     */
    @Bean
    SourceFloorPolicy sourceFloorPolicy(
            @Value("${report.research.min-active-sources:3}") int minActiveSources,
            @Value("${report.research.min-contributing-sections:3}") int minContributingSections) {
        return new SourceFloorPolicy(minActiveSources, minContributingSections);
    }

    /**
     * Translates the scorer's configuration, resolving domain-override names to the enum here so an
     * unknown name fails at startup instead of silently scoring as {@code SOCIAL} for months.
     */
    @Bean
    ScoringPolicy scoringPolicy(ScoringProperties scoring) {
        java.util.Map<String, SourceDomain> overrides = new java.util.LinkedHashMap<>();
        scoring.domainOverrides().forEach((prefix, domain) -> {
            try {
                overrides.put(prefix, SourceDomain.valueOf(domain.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("report.scoring.domain-overrides: '" + domain
                        + "' is not a SourceDomain. Known: "
                        + java.util.Arrays.toString(SourceDomain.values()), e);
            }
        });
        return new ScoringPolicy(scoring.crossSourceThreshold(), scoring.crossSourceBonus(),
                scoring.maxEngagementBonus(), scoring.engagementDivisor(),
                scoring.strongThreshold(), scoring.moderateThreshold(), overrides,
                java.util.Set.copyOf(scoring.mutedTopics()), scoring.muteMultiplier());
    }

    @Bean
    EolPolicy eolPolicy(EolProperties eol) {
        return new EolPolicy(eol.enabled(), eol.warnWithinMonths(), eol.validAsOf(),
                eol.entries().stream()
                        .map(e -> new EolNotice(e.name(), e.date(), eol.validAsOf()))
                        .toList());
    }

    @Bean
    SemanticClusteringPolicy semanticClusteringPolicy(SemanticClusteringProperties clustering) {
        return new SemanticClusteringPolicy(clustering.enabled(), clustering.enforcing(),
                clustering.threshold(), clustering.maxClusterSize());
    }

    @Bean
    SectionsPolicy sectionsPolicy(SectionsProperties sections) {
        return new SectionsPolicy(java.util.Set.copyOf(sections.disabled()));
    }

    @Bean
    SelfCritiquePolicy selfCritiquePolicy(SelfCritiqueProperties critique) {
        return new SelfCritiquePolicy(critique.enabled(), critique.enforcing());
    }

    @Bean
    RetrospectivePolicy retrospectivePolicy(RetrospectiveProperties retrospective) {
        return new RetrospectivePolicy(retrospective.lookbackDays(), retrospective.maturityDays());
    }

    /**
     * Refuses to start when an X source is switched on without the credential it needs (K7).
     *
     * <p>{@code twitter.bearer-token} stopped being {@code @NotBlank} when X was disabled by
     * default: requiring the project's one paid credential for a source nobody fetches would make
     * "disabled" cost exactly what "enabled" cost. The fail-fast property it provided is worth
     * keeping though — a run that discovers a missing token at the first search has already spent
     * its other fetches — so it moves here, where both halves of the question are in scope.
     */
    @Bean
    org.springframework.beans.factory.InitializingBean twitterCredentialCheck(
            SourceDisablePolicy sources, TwitterProperties twitter) {
        return () -> {
            if (sources.anyTwitterEnabled()
                    && (twitter.bearerToken() == null || twitter.bearerToken().isBlank())) {
                throw new IllegalStateException(
                        "An X/Twitter source is enabled but TWITTER_BEARER_TOKEN is blank. Set it, or "
                                + "add the source to report.sources.disabled.");
            }
        };
    }

    @Bean
    SourceDisablePolicy sourceDisablePolicy(SourcesProperties sources) {
        return new SourceDisablePolicy(java.util.Set.copyOf(sources.disabled()));
    }

    @Bean
    DeliveryGuardPolicy deliveryGuardPolicy(DeliveryProperties delivery) {
        return new DeliveryGuardPolicy(delivery.guardEnabled());
    }

    @Bean
    SavedBacklogPolicy savedBacklogPolicy(SavedProperties saved, FeedbackProperties feedback) {
        return new SavedBacklogPolicy(saved.enabled(), saved.maxItems(), feedback.lookbackDays());
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
