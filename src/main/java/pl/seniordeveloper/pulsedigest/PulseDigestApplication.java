package pl.seniordeveloper.pulsedigest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LabAnnouncementsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ArxivProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CncfLandscapeProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ConferenceTalksProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DedupProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DependencyProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EmailProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EolProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DeliveryProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SavedProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RetrospectiveProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CallForPapersProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SectionsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SelfCritiqueProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SemanticClusteringProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SourcesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubApiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubReleasesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HackerNewsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HuggingFaceProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.InterestProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenAiProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenJdkProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ProductHuntProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RedditProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.PreScoringProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReaderProfileProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportHistoryProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ScoringProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RssProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SecurityAdvisoriesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechnologyRadarProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        TwitterProperties.class,
        OpenAiProperties.class,
        LlmProperties.class,
        ReportHistoryProperties.class,
        ReaderProfileProperties.class,
        PreScoringProperties.class,
        ThinWeekProperties.class,
        EmailProperties.class,
        ResearchProperties.class,
        ScoringProperties.class,
        HackerNewsProperties.class,
        GithubApiProperties.class,
        GithubProperties.class,
        RssProperties.class,
        RedditProperties.class,
        ArxivProperties.class,
        GithubReleasesProperties.class,
        HuggingFaceProperties.class,
        ProductHuntProperties.class,
        SecurityAdvisoriesProperties.class,
        OpenJdkProperties.class,
        CncfLandscapeProperties.class,
        TechnologyRadarProperties.class,
        ConferenceTalksProperties.class,
        LabAnnouncementsProperties.class,
        DedupProperties.class,
        DependencyProfileProperties.class,
        InterestProfileProperties.class,
        FeedbackProperties.class,
        EolProperties.class,
        SavedProperties.class,
        DeliveryProperties.class,
        SourcesProperties.class,
        RetrospectiveProperties.class,
        SelfCritiqueProperties.class,
        CallForPapersProperties.class,
        SectionsProperties.class,
        SemanticClusteringProperties.class,
        SocialProperties.class
})
public final class PulseDigestApplication {

    private PulseDigestApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(PulseDigestApplication.class, args);
    }
}
