package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DbEngineRanking;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.NvdVulnerability;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PackageTrend;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
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
    private final HuggingFaceTrendingAdapter huggingFaceTrendingAdapter;
    private final ProductHuntAdapter productHuntAdapter;
    private final SecurityAdvisoryAdapter securityAdvisoryAdapter;
    private final NvdApiAdapter nvdApiAdapter;
    private final LibrariesIoAdapter librariesIoAdapter;
    private final OpenJdkJepAdapter openJdkJepAdapter;
    private final CncfLandscapeAdapter cncfLandscapeAdapter;
    private final TechnologyRadarAdapter technologyRadarAdapter;
    private final ConferenceTalksAdapter conferenceTalksAdapter;
    private final DbEnginesAdapter dbEnginesAdapter;
    private final LabAnnouncementsAdapter labAnnouncementsAdapter;

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

    @Override
    public List<HuggingFaceModel> fetchTrendingModels() {
        return huggingFaceTrendingAdapter.fetchTrendingModels();
    }

    @Override
    public List<ProductHuntPost> fetchProductLaunches() {
        return productHuntAdapter.fetchProductLaunches();
    }

    @Override
    public List<SecurityAdvisory> fetchSecurityAdvisories() {
        return securityAdvisoryAdapter.fetchSecurityAdvisories();
    }

    @Override
    public List<NvdVulnerability> fetchNvdVulnerabilities() {
        return nvdApiAdapter.fetchNvdVulnerabilities();
    }

    @Override
    public List<PackageTrend> fetchPackageTrends() {
        return librariesIoAdapter.fetchPackageTrends();
    }

    @Override
    public List<JepUpdate> fetchJepUpdates() {
        return openJdkJepAdapter.fetchJepUpdates();
    }

    @Override
    public List<CncfProjectUpdate> fetchCncfLandscapeChanges() {
        return cncfLandscapeAdapter.fetchCncfLandscapeChanges();
    }

    @Override
    public List<RadarEntry> fetchTechRadarEntries() {
        return technologyRadarAdapter.fetchTechRadarEntries();
    }

    @Override
    public List<ConferenceTalk> fetchConferenceTalks() {
        return conferenceTalksAdapter.fetchConferenceTalks();
    }

    @Override
    public List<DbEngineRanking> fetchDbEngineRankings() {
        return dbEnginesAdapter.fetchDbEngineRankings();
    }

    @Override
    public List<LabAnnouncement> fetchLabAnnouncements() {
        return labAnnouncementsAdapter.fetchAnnouncements();
    }
}
