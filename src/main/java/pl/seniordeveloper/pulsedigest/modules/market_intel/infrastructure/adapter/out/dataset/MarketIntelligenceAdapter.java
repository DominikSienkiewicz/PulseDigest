package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MarketIntelligenceAdapter implements MarketIntelligencePort {

    private final TwitterSearchAdapter twitterSearchAdapter;
    private final HackerNewsSearchAdapter hackerNewsSearchAdapter;
    private final GithubSearchAdapter githubSearchAdapter;
    private final RssFeedAdapter rssFeedAdapter;
    private final RedditSearchAdapter redditSearchAdapter;
    private final ArxivSearchAdapter arxivSearchAdapter;
    private final GithubReleasesAdapter githubReleasesAdapter;

    @Override
    public List<Tweet> fetchInfluencerTweets() {
        return twitterSearchAdapter.searchInfluencerTweets();
    }

    @Override
    public List<Tweet> fetchTopicTweets() {
        return twitterSearchAdapter.searchTopicTweets();
    }

    @Override
    public List<Tweet> fetchAnthropicTweets() {
        return twitterSearchAdapter.searchAnthropicTweets();
    }

    @Override
    public List<HackerNewsPost> fetchTopDiscussions() {
        return hackerNewsSearchAdapter.fetchTopDiscussions();
    }

    @Override
    public List<GithubRepo> fetchTrendingRepos() {
        return githubSearchAdapter.fetchTrendingRepos();
    }

    @Override
    public List<RssItem> fetchRssItems() {
        return rssFeedAdapter.fetchAll();
    }

    @Override
    public List<RedditPost> fetchRedditPosts() {
        return redditSearchAdapter.fetchTopPosts();
    }

    @Override
    public List<ResearchPaper> fetchLatestPapers() {
        return arxivSearchAdapter.fetchLatestPapers();
    }

    @Override
    public List<SoftwareRelease> fetchLatestReleases() {
        return githubReleasesAdapter.fetchLatestReleases();
    }
}
