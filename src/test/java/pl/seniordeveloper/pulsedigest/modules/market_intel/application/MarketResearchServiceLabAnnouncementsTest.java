package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CncfProjectUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ConferenceTalk;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.JepUpdate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProductHuntPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TechDemandSignal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab announcements were the only source whose URLs bypassed canonicalization at the fetch boundary,
 * so the same post could be re-published across editions and reader down-votes never matched it.
 */
class MarketResearchServiceLabAnnouncementsTest {

    @Test
    void labAnnouncementUrlsAreCanonicalizedAtTheFetchBoundary() {
        MarketResearchService service = new MarketResearchService(
                new LabOnlyPort(), researchPolicy(), Runnable::run);

        ResearchResult result = service.fetchAndFilter();

        assertThat(result.labAnnouncements())
                .singleElement()
                .satisfies(announcement ->
                        assertThat(announcement.url()).isEqualTo("https://www.anthropic.com/news/claude-5"));
    }

    private static ResearchPolicy researchPolicy() {
        return new ResearchPolicy(10, 2, List.of("authority"), List.of("java", "ai"));
    }

    /** Every source empty except lab announcements, whose single URL carries tracking params. */
    private static final class LabOnlyPort implements MarketIntelligencePort {

        @Override
        public List<Tweet> fetchInfluencerTweets() {
            return List.of();
        }

        @Override
        public List<Tweet> fetchTopicTweets() {
            return List.of();
        }

        @Override
        public List<Tweet> fetchAnthropicTweets() {
            return List.of();
        }

        @Override
        public List<HackerNewsPost> fetchTopDiscussions() {
            return List.of();
        }

        @Override
        public List<GithubRepo> fetchTrendingRepos() {
            return List.of();
        }

        @Override
        public List<RssItem> fetchRssItems() {
            return List.of();
        }

        @Override
        public List<RedditPost> fetchRedditPosts() {
            return List.of();
        }

        @Override
        public List<ResearchPaper> fetchLatestPapers() {
            return List.of();
        }

        @Override
        public List<SoftwareRelease> fetchLatestReleases() {
            return List.of();
        }

        @Override
        public List<HuggingFaceModel> fetchTrendingModels() {
            return List.of();
        }

        @Override
        public List<ProductHuntPost> fetchProductLaunches() {
            return List.of();
        }

        @Override
        public List<SecurityAdvisory> fetchSecurityAdvisories() {
            return List.of();
        }

        @Override
        public List<JepUpdate> fetchJepUpdates() {
            return List.of();
        }

        @Override
        public List<CncfProjectUpdate> fetchCncfLandscapeChanges() {
            return List.of();
        }

        @Override
        public List<RadarEntry> fetchTechRadarEntries() {
            return List.of();
        }

        @Override
        public List<ConferenceTalk> fetchConferenceTalks() {
            return List.of();
        }

        @Override
        public List<SocialPost> fetchSocialPosts() {
            return List.of();
        }

        @Override
        public List<LabAnnouncement> fetchLabAnnouncements() {
            return List.of(new LabAnnouncement("Claude 5 released",
                    "https://www.anthropic.com/news/claude-5?utm_source=newsletter&utm_medium=email",
                    "New frontier model", "Anthropic News", LocalDateTime.parse("2026-05-14T10:00:00")));
        }

        @Override
        public Optional<TechDemandSignal> fetchTechDemand() {
            return Optional.empty();
        }
    }
}
