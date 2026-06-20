package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;

import java.util.List;
import java.util.Optional;

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

    List<HuggingFaceModel> fetchTrendingModels();

    List<ProductHuntPost> fetchProductLaunches();

    List<SecurityAdvisory> fetchSecurityAdvisories();

    List<JepUpdate> fetchJepUpdates();

    List<CncfProjectUpdate> fetchCncfLandscapeChanges();

    List<RadarEntry> fetchTechRadarEntries();

    List<ConferenceTalk> fetchConferenceTalks();

    List<LabAnnouncement> fetchLabAnnouncements();

    Optional<TechDemandSignal> fetchTechDemand();
}
