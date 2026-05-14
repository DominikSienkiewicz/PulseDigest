package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Unified configuration properties for PulseDigest report generation.
 */
@ConfigurationProperties(prefix = "report")
@Validated
public record ReportProperties(
        @Min(0)
        int cacheTtlMinutes,
        @Min(0)
        int minGenerationIntervalMinutes,
        @Valid @NotNull
        EmailProperties email,
        @Valid @NotNull
        ResearchProperties research,
        @Valid @NotNull
        HackerNewsProperties hackerNews,
        @Valid @NotNull
        GithubProperties github,
        @Valid @NotNull
        RssProperties rss,
        @Valid @NotNull
        RedditProperties reddit,
        @Valid @NotNull
        ArxivProperties arxiv,
        @Valid @NotNull
        GithubReleasesProperties githubReleases,
        @Valid @NotNull
        TrendProperties trend,
        @Valid @NotNull
        HuggingFaceProperties huggingFace,
        @Valid @NotNull
        ProductHuntProperties productHunt,
        @Valid @NotNull
        SecurityAdvisoriesProperties securityAdvisories,
        @Valid @NotNull
        NvdProperties nvd,
        @Valid @NotNull
        LibrariesIoProperties librariesIo,
        @Valid @NotNull
        OpenJdkProperties openJdk,
        @Valid @NotNull
        CncfLandscapeProperties cncfLandscape,
        @Valid @NotNull
        TechnologyRadarProperties technologyRadar,
        @Valid @NotNull
        ConferenceTalksProperties conferenceTalks,
        @Valid @NotNull
        DbEnginesProperties dbEngines
) {

    public record EmailProperties(
            @NotBlank
            String resendApiKey,
            @NotBlank
            String from,
            @NotBlank
            String to
    ) {
    }

    public record ResearchProperties(
            @Min(0)
            int minLikes,
            @Min(1)
            int daysBack,
            @NotEmpty
            List<@NotBlank String> authorityUsernames
    ) {
    }

    public record HackerNewsProperties(
            @NotBlank
            String baseUrl,
            @NotEmpty
            List<@NotBlank String> keywords,
            @Min(1)
            int limit,
            @Min(0)
            int minScore
    ) {
    }

    public record GithubProperties(
            @NotBlank
            String query,
            @Min(1)
            int limit
    ) {
    }

    public record RssProperties(
            @Min(1)
            int limit,
            @Valid @NotEmpty
            List<FeedConfig> feeds
    ) {
        public record FeedConfig(@NotBlank String name, @NotBlank String url) {
        }
    }

    public record RedditProperties(
            @NotEmpty
            List<@NotBlank String> subreddits,
            @Min(1)
            int limit,
            @Min(0)
            int minScore
    ) {
    }

    /**
     * Configuration for arXiv research paper fetching.
     */
    public record ArxivProperties(
            @NotBlank
            String categories,
            @NotBlank
            String keywords,
            @Min(1)
            int maxResults,
            @Min(1)
            int lookbackHours
    ) {
    }

    /**
     * Configuration for GitHub Releases monitoring.
     */
    public record GithubReleasesProperties(
            @NotEmpty
            List<@NotBlank String> repositories,
            @Min(1)
            int lookbackHours
    ) {
    }

    /**
     * Configuration for trend detection across historical reports.
     */
    public record TrendProperties(
            boolean enabled,
            @Min(1)
            int lookbackDays,
            @Min(1)
            int minOccurrences,
            @Min(1)
            int maxClusters
    ) {
    }

    /**
     * Configuration for the Hugging Face Hub trending models adapter.
     */
    public record HuggingFaceProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int limit,
            @Min(0)
            long minLikes,
            @Min(0)
            long minDownloads,
            @NotEmpty
            List<@NotBlank String> relevantPipelines
    ) {
    }

    /**
     * Configuration for the Product Hunt launches adapter.
     */
    public record ProductHuntProperties(
            @NotBlank
            String baseUrl,
            String developerToken,
            @Min(0)
            int minVotes,
            @Min(1)
            int lookbackHours,
            @NotEmpty
            List<@NotBlank String> relevantTopics
    ) {
    }

    /**
     * Configuration for the GitHub Security Advisories adapter.
     */
    public record SecurityAdvisoriesProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int limit,
            @Min(1)
            int lookbackHours,
            @NotEmpty
            List<@NotBlank String> minSeverities,
            @NotEmpty
            List<@NotBlank String> relevantEcosystems
    ) {
    }

    /**
     * Configuration for the NIST National Vulnerability Database (NVD) API adapter.
     */
    public record NvdProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int resultsPerPage,
            @Min(1)
            int lookbackHours,
            @NotEmpty
            List<@NotBlank String> minSeverities
    ) {
    }

    /**
     * Configuration for the Libraries.io package trends adapter.
     */
    public record LibrariesIoProperties(
            @NotBlank
            String baseUrl,
            String apiKey,
            @Min(1)
            int limit,
            @NotEmpty
            List<@NotBlank String> platforms,
            @Min(1)
            int lookbackDays
    ) {
    }

    /**
     * Configuration for the OpenJDK JEP tracker adapter.
     */
    public record OpenJdkProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int lookbackDays,
            @NotEmpty
            List<@NotBlank String> relevantStatuses
    ) {
    }

    /**
     * Configuration for the CNCF Landscape tracker adapter.
     */
    public record CncfLandscapeProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int lookbackDays,
            @NotEmpty
            List<@NotBlank String> relevantStatuses
    ) {
    }

    /**
     * Configuration for the Thoughtworks Technology Radar adapter.
     */
    public record TechnologyRadarProperties(
            @NotBlank
            String baseUrl,
            @NotBlank
            String dataPath,
            @Min(1)
            int lookbackMonths
    ) {
    }

    /**
     * Configuration for the YouTube Conference Talks adapter.
     */
    public record ConferenceTalksProperties(
            @NotBlank
            String baseUrl,
            String apiKey,
            @Min(1)
            int lookbackDays,
            @Min(1)
            int maxResults,
            @Valid @NotEmpty
            List<ChannelConfig> channels
    ) {
        public record ChannelConfig(
                @NotBlank String channelName,
                @NotBlank String conferenceName,
                @NotBlank String channelId
        ) {
        }
    }

    /**
     * Configuration for the DB-Engines ranking adapter.
     */
    public record DbEnginesProperties(
            @NotBlank
            String baseUrl,
            @Min(1)
            int lookbackDays,
            @Min(0)
            int minScoreChange
    ) {
    }
}
