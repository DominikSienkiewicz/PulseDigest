package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregate of all raw data collected from external intelligence sources.
 */
public record ResearchResult(
        List<Tweet> tweets,
        List<HackerNewsPost> hackerNewsPosts,
        List<GithubRepo> githubRepos,
        List<RssItem> rssItems,
        List<RedditPost> redditPosts,
        List<ResearchPaper> papers,
        List<SoftwareRelease> releases,
        LocalDateTime collectedAt,
        int rawTweetsCount,
        int rawHackerNewsCount,
        int rawGithubCount,
        int rawRssCount,
        int rawRedditCount
) {

    public boolean isEmpty() {
        return tweets.isEmpty() && hackerNewsPosts.isEmpty() && githubRepos.isEmpty()
                && rssItems.isEmpty() && redditPosts.isEmpty()
                && papers.isEmpty() && releases.isEmpty();
    }

    public int rawTotalCount() {
        return rawTweetsCount + rawHackerNewsCount + rawGithubCount
                + rawRssCount + rawRedditCount + papers.size() + releases.size();
    }

    public int activeSourceCount() {
        int n = 0;
        if (!tweets.isEmpty()) {
            n++;
        }
        if (!hackerNewsPosts.isEmpty()) {
            n++;
        }
        if (!githubRepos.isEmpty()) {
            n++;
        }
        if (!rssItems.isEmpty()) {
            n++;
        }
        if (!redditPosts.isEmpty()) {
            n++;
        }
        if (!papers.isEmpty()) {
            n++;
        }
        if (!releases.isEmpty()) {
            n++;
        }
        return n;
    }

    public String summary() {
        return "Zebrano: %d tweetów, %d HN, %d GH, %d RSS, %d Reddit, %d papers, %d releases — łącznie %d itemów, o %s"
                .formatted(rawTweetsCount, rawHackerNewsCount, rawGithubCount,
                        rawRssCount, rawRedditCount, papers.size(), releases.size(),
                        rawTweetsCount + rawHackerNewsCount + rawGithubCount
                                + rawRssCount + rawRedditCount + papers.size() + releases.size(),
                        collectedAt);
    }
}
