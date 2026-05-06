package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Unified configuration properties for PulseDigest report generation.
 */
@ConfigurationProperties(prefix = "report")
public record ReportProperties(
        int cacheTtlMinutes,
        int minGenerationIntervalMinutes,
        EmailProperties email,
        ResearchProperties research,
        HackerNewsProperties hackerNews,
        GithubProperties github,
        RssProperties rss,
        RedditProperties reddit,
        ArxivProperties arxiv,
        GithubReleasesProperties githubReleases,
        TrendProperties trend,
        HuggingFaceProperties huggingFace,
        ProductHuntProperties productHunt,
        SecurityAdvisoriesProperties securityAdvisories,
        NvdProperties nvd,
        LibrariesIoProperties librariesIo,
        OpenJdkProperties openJdk,
        CncfLandscapeProperties cncfLandscape,
        TechnologyRadarProperties technologyRadar,
        ConferenceTalksProperties conferenceTalks,
        DbEnginesProperties dbEngines
) {

    public record EmailProperties(
            String resendApiKey,
            String from,
            String to
    ) {
    }

    public record ResearchProperties(
            int minLikes,
            int daysBack,
            List<String> authorityUsernames
    ) {
    }

    public record HackerNewsProperties(
            String baseUrl,
            List<String> keywords,
            int limit,
            int minScore
    ) {
    }

    public record GithubProperties(
            String query,
            int limit
    ) {
    }

    public record RssProperties(
            int limit,
            List<FeedConfig> feeds
    ) {
        public record FeedConfig(String name, String url) {
        }
    }

    public record RedditProperties(
            List<String> subreddits,
            int limit,
            int minScore
    ) {
    }

    /**
     * Configuration for arXiv research paper fetching.
     */
    public record ArxivProperties(
            String categories,
            String keywords,
            int maxResults,
            int lookbackHours
    ) {
    }

    /**
     * Configuration for GitHub Releases monitoring.
     */
    public record GithubReleasesProperties(
            List<String> repositories,
            int lookbackHours
    ) {
    }

    /**
     * Configuration for trend detection across historical reports.
     */
    public record TrendProperties(
            boolean enabled,
            int lookbackDays,
            int minOccurrences,
            int maxClusters
    ) {
    }

    /**
     * Configuration for the Hugging Face Hub trending models adapter.
     */
    public record HuggingFaceProperties(
            String baseUrl,
            int limit,
            long minLikes,
            long minDownloads,
            List<String> relevantPipelines
    ) {
    }

    /**
     * Configuration for the Product Hunt launches adapter.
     */
    public record ProductHuntProperties(
            String baseUrl,
            String developerToken,
            int minVotes,
            int lookbackHours,
            List<String> relevantTopics
    ) {
    }

    /**
     * Configuration for the GitHub Security Advisories adapter.
     */
    public record SecurityAdvisoriesProperties(
            String baseUrl,
            int limit,
            int lookbackHours,
            List<String> minSeverities,
            List<String> relevantEcosystems
    ) {
    }

    /**
     * Configuration for the NIST National Vulnerability Database (NVD) API adapter.
     */
    public record NvdProperties(
            String baseUrl,
            int resultsPerPage,
            int lookbackHours,
            List<String> minSeverities
    ) {
    }

    /**
     * Configuration for the Libraries.io package trends adapter.
     */
    public record LibrariesIoProperties(
            String baseUrl,
            String apiKey,
            int limit,
            List<String> platforms,
            int lookbackDays
    ) {
    }

    /**
     * Configuration for the OpenJDK JEP tracker adapter.
     */
    public record OpenJdkProperties(
            String baseUrl,
            int lookbackDays,
            List<String> relevantStatuses
    ) {
    }

    /**
     * Configuration for the CNCF Landscape tracker adapter.
     */
    public record CncfLandscapeProperties(
            String baseUrl,
            int lookbackDays,
            List<String> relevantStatuses
    ) {
    }

    /**
     * Configuration for the Thoughtworks Technology Radar adapter.
     */
    public record TechnologyRadarProperties(
            String baseUrl,
            String dataPath,
            int lookbackMonths
    ) {
    }

    /**
     * Configuration for the YouTube Conference Talks adapter.
     */
    public record ConferenceTalksProperties(
            String baseUrl,
            String apiKey,
            int lookbackDays,
            int maxResults,
            List<ChannelConfig> channels
    ) {
        public record ChannelConfig(String channelName, String conferenceName, String channelId) {
        }
    }

    /**
     * Configuration for the DB-Engines ranking adapter.
     */
    public record DbEnginesProperties(
            String baseUrl,
            int lookbackDays,
            int minScoreChange
    ) {
    }
}
