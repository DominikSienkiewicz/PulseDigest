package pl.seniordeveloper.pulsedigest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ArxivProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.CncfLandscapeProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ConferenceTalksProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DbEnginesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.EmailProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.GithubReleasesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HackerNewsProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HuggingFaceProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LibrariesIoProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.NvdProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.OpenJdkProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ProductHuntProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RedditProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportRuntimeProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RssProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SecurityAdvisoriesProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TechnologyRadarProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TrendProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        TwitterProperties.class,
        ReportRuntimeProperties.class,
        EmailProperties.class,
        ResearchProperties.class,
        HackerNewsProperties.class,
        GithubProperties.class,
        RssProperties.class,
        RedditProperties.class,
        ArxivProperties.class,
        GithubReleasesProperties.class,
        TrendProperties.class,
        HuggingFaceProperties.class,
        ProductHuntProperties.class,
        SecurityAdvisoriesProperties.class,
        NvdProperties.class,
        LibrariesIoProperties.class,
        OpenJdkProperties.class,
        CncfLandscapeProperties.class,
        TechnologyRadarProperties.class,
        ConferenceTalksProperties.class,
        DbEnginesProperties.class
})
public final class PulseDigestApplication {

    private PulseDigestApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(PulseDigestApplication.class, args);
    }
}
