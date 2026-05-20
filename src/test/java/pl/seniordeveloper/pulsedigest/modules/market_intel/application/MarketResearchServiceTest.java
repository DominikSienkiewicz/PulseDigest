package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SecurityAdvisory;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketResearchServiceTest {

    @Test
    void fetchAndFilterAggregatesCanonicalizesAndFiltersSources() {
        MarketResearchService service = new MarketResearchService(
                new StubMarketIntelligencePort(false),
                researchPolicy(),
                Runnable::run);

        ResearchResult result = service.fetchAndFilter();

        assertThat(result.tweets())
                .extracting(Tweet::id)
                .containsExactly(
                        "topic-good",
                        "tracked-null-date",
                        "tracked-invalid-date",
                        "tracked-relevant",
                        "authority-anthropic",
                        "authority-good");
        assertThat(result.hackerNewsPosts().getFirst().url()).doesNotContain("utm_source");
        assertThat(result.githubRepos().getFirst().url()).doesNotContain("utm_source");
        assertThat(result.redditPosts()).hasSize(1);
        assertThat(result.rawTotalCount()).isEqualTo(27);
        assertThat(result.activeSourceCount()).isEqualTo(17);
        assertThat(result.sourceFetchReports()).hasSize(20);
        assertThat(result.sourceFetchReports())
                .allMatch(report -> report.status() == SourceFetchStatus.SUCCESS);
    }

    @Test
    void fetchAndFilterRecordsFailedSourceAndContinues() {
        MarketResearchService service = new MarketResearchService(
                new StubMarketIntelligencePort(true),
                researchPolicy(),
                Runnable::run);

        ResearchResult result = service.fetchAndFilter();

        assertThat(result.redditPosts()).isEmpty();
        assertThat(result.rawTotalCount()).isEqualTo(26);
        assertThat(result.sourceFetchReports())
                .anySatisfy(report -> {
                    assertThat(report.sourceName()).isEqualTo("Reddit");
                    assertThat(report.status()).isEqualTo(SourceFetchStatus.FAILED);
                    assertThat(report.errorMessage()).isEqualTo("reddit down");
                });
    }

    private static ResearchPolicy researchPolicy() {
        return new ResearchPolicy(10, 2, List.of("authority"));
    }

    private static String recentHoursAgo(int hours) {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .minusHours(hours)
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static String daysAgo(int days) {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .minusDays(days)
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static String trackedUrl(String path) {
        return "https://example.com/" + path + "?utm_source=newsletter&utm_medium=email";
    }

    private static final class StubMarketIntelligencePort implements MarketIntelligencePort {

        private final boolean failReddit;
        private final LocalDateTime now = LocalDateTime.parse("2026-05-14T10:00:00");

        private StubMarketIntelligencePort(boolean failReddit) {
            this.failReddit = failReddit;
        }

        @Override
        public List<Tweet> fetchInfluencerTweets() {
            return List.of(
                    new Tweet("authority-good", "status update", "authority", "Authority", recentHoursAgo(2),
                            20, 1, 1),
                    new Tweet("tracked-relevant", "Java agent platform", "tracked", "Tracked", recentHoursAgo(2),
                            30, 1, 1),
                    new Tweet("tracked-null-date", "AI coding model", "tracked", "Tracked", null, 40, 1, 1),
                    new Tweet("tracked-invalid-date", "LLM automation", "tracked", "Tracked", "not-a-date",
                            35, 1, 1),
                    new Tweet("tracked-irrelevant", "gardening update", "tracked", "Tracked", recentHoursAgo(1),
                            90, 1, 1),
                    new Tweet("tracked-low-likes", "Java release", "tracked", "Tracked", recentHoursAgo(1),
                            5, 1, 1),
                    new Tweet("tracked-stale", "Java release", "tracked", "Tracked", daysAgo(5), 70, 1, 1));
        }

        @Override
        public List<Tweet> fetchTopicTweets() {
            return List.of(
                    new Tweet("topic-good", "Topic match", "topic", "Topic", recentHoursAgo(1), 50, 1, 1),
                    new Tweet("topic-low", "Topic low", "topic", "Topic", recentHoursAgo(1), 5, 1, 1),
                    new Tweet("topic-stale", "Topic stale", "topic", "Topic", daysAgo(5), 90, 1, 1));
        }

        @Override
        public List<Tweet> fetchAnthropicTweets() {
            return List.of(new Tweet("authority-anthropic", "status update", "authority", "Authority",
                    recentHoursAgo(1), 25, 1, 1));
        }

        @Override
        public List<HackerNewsPost> fetchTopDiscussions() {
            return List.of(new HackerNewsPost("HN", trackedUrl("hn"), 42));
        }

        @Override
        public List<GithubRepo> fetchTrendingRepos() {
            return List.of(new GithubRepo("owner/repo", "desc", 1_000, trackedUrl("repo")));
        }

        @Override
        public List<RssItem> fetchRssItems() {
            return List.of(new RssItem("RSS", trackedUrl("rss"), "desc", "Java"));
        }

        @Override
        public List<RedditPost> fetchRedditPosts() {
            if (failReddit) {
                throw new IllegalStateException("reddit down");
            }
            return List.of(new RedditPost("Reddit", trackedUrl("reddit"), 10, "java"));
        }

        @Override
        public List<ResearchPaper> fetchLatestPapers() {
            return List.of(new ResearchPaper("2401.1", "Paper", "abstract", List.of("Ada"),
                    trackedUrl("paper"), "cs.SE", now));
        }

        @Override
        public List<SoftwareRelease> fetchLatestReleases() {
            return List.of(new SoftwareRelease("spring/project", "v1", "notes", trackedUrl("release"), now));
        }

        @Override
        public List<HuggingFaceModel> fetchTrendingModels() {
            return List.of(new HuggingFaceModel("org/model", "org", 10, 1, now, "text", trackedUrl("model")));
        }

        @Override
        public List<ProductHuntPost> fetchProductLaunches() {
            return List.of(new ProductHuntPost("Launch", "tagline", trackedUrl("launch"), 10, List.of("Dev"), now));
        }

        @Override
        public List<SecurityAdvisory> fetchSecurityAdvisories() {
            return List.of(new SecurityAdvisory("GHSA-1", "summary", "HIGH", now, List.of("Maven"),
                    trackedUrl("ghsa")));
        }

        @Override
        public List<NvdVulnerability> fetchNvdVulnerabilities() {
            return List.of(new NvdVulnerability("CVE-1", "desc", 7.5, "HIGH", now, List.of("jdk"),
                    trackedUrl("nvd")));
        }

        @Override
        public List<PackageTrend> fetchPackageTrends() {
            return List.of(new PackageTrend("lib", "Maven", "desc", 100, 1_000, trackedUrl("lib"), now));
        }

        @Override
        public List<JepUpdate> fetchJepUpdates() {
            return List.of(new JepUpdate("JEP 1", "title", "delivered", now, trackedUrl("jep")));
        }

        @Override
        public List<CncfProjectUpdate> fetchCncfLandscapeChanges() {
            return List.of(new CncfProjectUpdate("Kubernetes", "orchestration", "graduated", "desc",
                    trackedUrl("cncf"), now));
        }

        @Override
        public List<RadarEntry> fetchTechRadarEntries() {
            return List.of(new RadarEntry("Tool", "adopt", "tools", "desc", trackedUrl("radar"), now));
        }

        @Override
        public List<ConferenceTalk> fetchConferenceTalks() {
            return List.of(new ConferenceTalk("Talk", "Devoxx", "Devoxx", trackedUrl("talk"), now, 1_000));
        }

        @Override
        public List<DbEngineRanking> fetchDbEngineRankings() {
            return List.of(new DbEngineRanking("PostgreSQL", 1, 0, 1_000.0, 1.0, trackedUrl("db"), now));
        }

        @Override
        public List<pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.LabAnnouncement>
                fetchLabAnnouncements() {
            return List.of();
        }
    }
}
