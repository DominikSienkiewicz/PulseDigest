package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

public record RedditPost(
        String title,
        String url,
        int score,
        String subreddit
) {
}
