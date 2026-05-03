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
        GithubReleasesProperties githubReleases
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
            String query,
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
}
