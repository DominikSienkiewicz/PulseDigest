package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;

import java.util.List;

/**
 * Output port for fetching raw intelligence data from external sources.
 */
public interface MarketIntelligencePort {
    List<Tweet> fetchInfluencerTweets();

    List<Tweet> fetchTopicTweets();

    List<Tweet> fetchAnthropicTweets();

    List<HackerNewsPost> fetchTopDiscussions();

    List<GithubRepo> fetchTrendingRepos();

    List<RssItem> fetchRssItems();

    List<RedditPost> fetchRedditPosts();

    List<ResearchPaper> fetchLatestPapers();

    List<SoftwareRelease> fetchLatestReleases();
}
