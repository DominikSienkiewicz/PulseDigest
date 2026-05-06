package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class MarketResearchService {

    private static final Set<String> RELEVANCE_KEYWORDS = Set.of(
            "ai", "llm", "agent", "model", "gpt", "claude", "coding", "engineer",
            "programming", "developer", "software", "startup", "hiring", "job",
            "automation", "openai", "anthropic", "gemini", "copilot", "future",
            "technology", "tech", "ml", "machine learning", "data", "api",
            "agentic", "orchestrat", "langchain", "spring", "java", "python",
            "azure", "aws", "cloud", "product", "saas", "inference", "benchmark",
            "2026", "intelligence", "reasoning", "context", "token", "rag", "vector"
    );
    private final MarketIntelligencePort intelligencePort;
    private final ReportProperties reportProperties;
    @Qualifier("dataFetchExecutor")
    private final Executor taskExecutor;

    public ResearchResult fetchAndFilter() {
        log.info("Starting market research data gathering...");

        CompletableFuture<List<Tweet>> futureInfluencer = CompletableFuture
                .supplyAsync(intelligencePort::fetchInfluencerTweets, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchInfluencerTweets failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<Tweet>> futureTopic = CompletableFuture
                .supplyAsync(intelligencePort::fetchTopicTweets, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchTopicTweets failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<Tweet>> futureAnthropic = CompletableFuture
                .supplyAsync(intelligencePort::fetchAnthropicTweets, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchAnthropicTweets failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<HackerNewsPost>> futureHn = CompletableFuture
                .supplyAsync(intelligencePort::fetchTopDiscussions, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchTopDiscussions failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<GithubRepo>> futureGh = CompletableFuture
                .supplyAsync(intelligencePort::fetchTrendingRepos, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchTrendingRepos failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<RssItem>> futureRss = CompletableFuture
                .supplyAsync(intelligencePort::fetchRssItems, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchRssItems failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<RedditPost>> futureReddit = CompletableFuture
                .supplyAsync(intelligencePort::fetchRedditPosts, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchRedditPosts failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<ResearchPaper>> futurePapers = CompletableFuture
                .supplyAsync(intelligencePort::fetchLatestPapers, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchLatestPapers failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<SoftwareRelease>> futureReleases = CompletableFuture
                .supplyAsync(intelligencePort::fetchLatestReleases, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchLatestReleases failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<HuggingFaceModel>> futureHf = CompletableFuture
                .supplyAsync(intelligencePort::fetchTrendingModels, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchTrendingModels failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<ProductHuntPost>> futurePh = CompletableFuture
                .supplyAsync(intelligencePort::fetchProductLaunches, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchProductLaunches failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<SecurityAdvisory>> futureAdvisories = CompletableFuture
                .supplyAsync(intelligencePort::fetchSecurityAdvisories, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchSecurityAdvisories failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<NvdVulnerability>> futureNvd = CompletableFuture
                .supplyAsync(intelligencePort::fetchNvdVulnerabilities, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchNvdVulnerabilities failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<PackageTrend>> futurePackages = CompletableFuture
                .supplyAsync(intelligencePort::fetchPackageTrends, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchPackageTrends failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<JepUpdate>> futureJep = CompletableFuture
                .supplyAsync(intelligencePort::fetchJepUpdates, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchJepUpdates failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<CncfProjectUpdate>> futureCncf = CompletableFuture
                .supplyAsync(intelligencePort::fetchCncfLandscapeChanges, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchCncfLandscapeChanges failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<RadarEntry>> futureRadar = CompletableFuture
                .supplyAsync(intelligencePort::fetchTechRadarEntries, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchTechRadarEntries failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<ConferenceTalk>> futureTalks = CompletableFuture
                .supplyAsync(intelligencePort::fetchConferenceTalks, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchConferenceTalks failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture<List<DbEngineRanking>> futureDb = CompletableFuture
                .supplyAsync(intelligencePort::fetchDbEngineRankings, taskExecutor)
                .exceptionally(ex -> {
                    log.warn("fetchDbEngineRankings failed: {}", ex.getMessage());
                    return List.of();
                });

        CompletableFuture.allOf(futureInfluencer, futureTopic, futureAnthropic, futureHn, futureGh,
                futureRss, futureReddit, futurePapers, futureReleases,
                futureHf, futurePh, futureAdvisories, futureNvd, futurePackages,
                futureJep, futureCncf, futureRadar, futureTalks, futureDb).join();

        List<Tweet> rawInfluencer = futureInfluencer.join();
        List<Tweet> rawTopic = futureTopic.join();
        List<Tweet> rawAnthropic = futureAnthropic.join();
        List<HackerNewsPost> rawHn = futureHn.join();
        List<GithubRepo> rawGh = futureGh.join();
        List<RssItem> rawRss = futureRss.join();
        List<RedditPost> rawReddit = futureReddit.join();
        List<ResearchPaper> rawPapers = futurePapers.join();
        List<SoftwareRelease> rawReleases = futureReleases.join();
        List<HuggingFaceModel> rawHf = futureHf.join();
        List<ProductHuntPost> rawPh = futurePh.join();
        List<SecurityAdvisory> rawAdvisories = futureAdvisories.join();
        List<NvdVulnerability> rawNvd = futureNvd.join();
        List<PackageTrend> rawPackages = futurePackages.join();
        List<JepUpdate> rawJep = futureJep.join();
        List<CncfProjectUpdate> rawCncf = futureCncf.join();
        List<RadarEntry> rawRadar = futureRadar.join();
        List<ConferenceTalk> rawTalks = futureTalks.join();
        List<DbEngineRanking> rawDb = futureDb.join();

        Set<String> authorityUsernames = Set.copyOf(reportProperties.research().authorityUsernames());
        int minLikes = reportProperties.research().minLikes();
        int daysBack = reportProperties.research().daysBack();
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusDays(daysBack);

        // Authority accounts get relaxed relevance check — their signal-to-noise is high by definition
        List<Tweet> filteredAuthority = Stream.concat(rawInfluencer.stream(), rawAnthropic.stream())
                .filter(t -> isFromAuthority(t, authorityUsernames))
                .filter(t -> t.likeCount() >= minLikes)
                .filter(t -> isRecent(t.createdAt(), cutoff))
                .toList();

        // All other tracked accounts — apply relevance keyword filter
        List<Tweet> filteredTracked = Stream.concat(rawInfluencer.stream(), rawAnthropic.stream())
                .filter(t -> !isFromAuthority(t, authorityUsernames))
                .filter(t -> t.likeCount() >= minLikes)
                .filter(t -> isRecent(t.createdAt(), cutoff))
                .filter(this::isRelevant)
                .toList();

        List<Tweet> filteredTopic = rawTopic.stream()
                .filter(t -> t.likeCount() >= minLikes)
                .filter(t -> isRecent(t.createdAt(), cutoff))
                .toList();

        List<Tweet> finalTweets = Stream.concat(
                        Stream.concat(filteredAuthority.stream(), filteredTracked.stream()),
                        filteredTopic.stream())
                .distinct()
                .sorted((a, b) -> Integer.compare(b.likeCount(), a.likeCount()))
                .limit(40)
                .toList();

        return new ResearchResult(
                finalTweets,
                rawHn.stream().map(MarketResearchService::canonicalize).toList(),
                rawGh.stream().map(MarketResearchService::canonicalize).toList(),
                rawRss.stream().map(MarketResearchService::canonicalize).toList(),
                rawReddit.stream().map(MarketResearchService::canonicalize).toList(),
                rawPapers.stream().map(MarketResearchService::canonicalize).toList(),
                rawReleases.stream().map(MarketResearchService::canonicalize).toList(),
                rawHf.stream().map(MarketResearchService::canonicalize).toList(),
                rawPh.stream().map(MarketResearchService::canonicalize).toList(),
                rawAdvisories.stream().map(MarketResearchService::canonicalize).toList(),
                rawNvd.stream().map(MarketResearchService::canonicalize).toList(),
                rawPackages.stream().map(MarketResearchService::canonicalize).toList(),
                rawJep.stream().map(MarketResearchService::canonicalize).toList(),
                rawCncf.stream().map(MarketResearchService::canonicalize).toList(),
                rawRadar.stream().map(MarketResearchService::canonicalize).toList(),
                rawTalks.stream().map(MarketResearchService::canonicalize).toList(),
                rawDb.stream().map(MarketResearchService::canonicalize).toList(),
                LocalDateTime.now(),
                rawInfluencer.size() + rawTopic.size() + rawAnthropic.size(),
                rawHn.size(), rawGh.size(), rawRss.size(), rawReddit.size()
        );
    }

    // ── URL canonicalization at the source boundary ──────────────────────────
    // Strip tracking params (utm_*, fbclid, etc.) zaraz po fetchu, zanim cokolwiek
    // dalej w pipeline zobaczy URL. Tweet pomijamy — record nie ma pola url.

    private static HackerNewsPost canonicalize(HackerNewsPost p) {
        return new HackerNewsPost(p.title(), UrlCanonicalizer.canonicalize(p.url()), p.points());
    }

    private static GithubRepo canonicalize(GithubRepo r) {
        return new GithubRepo(r.name(), r.description(), r.stars(), UrlCanonicalizer.canonicalize(r.url()));
    }

    private static RssItem canonicalize(RssItem r) {
        return new RssItem(r.title(), UrlCanonicalizer.canonicalize(r.url()), r.description(), r.feedName());
    }

    private static RedditPost canonicalize(RedditPost r) {
        return new RedditPost(r.title(), UrlCanonicalizer.canonicalize(r.url()), r.score(), r.subreddit());
    }

    private static ResearchPaper canonicalize(ResearchPaper p) {
        return new ResearchPaper(p.arxivId(), p.title(), p.abstractText(), p.authors(),
                UrlCanonicalizer.canonicalize(p.url()), p.primaryCategory(), p.publishedAt());
    }

    private static SoftwareRelease canonicalize(SoftwareRelease r) {
        return new SoftwareRelease(r.repoFullName(), r.version(), r.releaseNotesExcerpt(),
                UrlCanonicalizer.canonicalize(r.url()), r.releasedAt());
    }

    private static HuggingFaceModel canonicalize(HuggingFaceModel m) {
        return new HuggingFaceModel(m.id(), m.author(), m.downloads(), m.likes(),
                m.lastModified(), m.pipelineTag(), UrlCanonicalizer.canonicalize(m.url()));
    }

    private static ProductHuntPost canonicalize(ProductHuntPost p) {
        return new ProductHuntPost(p.name(), p.tagline(), UrlCanonicalizer.canonicalize(p.url()),
                p.votesCount(), p.topics(), p.createdAt());
    }

    private static SecurityAdvisory canonicalize(SecurityAdvisory a) {
        return new SecurityAdvisory(a.ghsaId(), a.summary(), a.severity(), a.publishedAt(),
                a.affectedEcosystems(), UrlCanonicalizer.canonicalize(a.url()));
    }

    private static NvdVulnerability canonicalize(NvdVulnerability v) {
        return new NvdVulnerability(v.cveId(), v.description(), v.cvssScore(), v.severity(),
                v.publishedAt(), v.affectedProducts(), UrlCanonicalizer.canonicalize(v.url()));
    }

    private static PackageTrend canonicalize(PackageTrend t) {
        return new PackageTrend(t.name(), t.platform(), t.description(), t.stars(),
                t.dependentProjects(), UrlCanonicalizer.canonicalize(t.url()), t.latestReleaseAt());
    }

    private static JepUpdate canonicalize(JepUpdate j) {
        return new JepUpdate(j.jepId(), j.title(), j.status(), j.updatedAt(),
                UrlCanonicalizer.canonicalize(j.url()));
    }

    private static CncfProjectUpdate canonicalize(CncfProjectUpdate c) {
        return new CncfProjectUpdate(c.projectName(), c.category(), c.status(), c.description(),
                UrlCanonicalizer.canonicalize(c.url()), c.updatedAt());
    }

    private static RadarEntry canonicalize(RadarEntry r) {
        return new RadarEntry(r.name(), r.ring(), r.quadrant(), r.description(),
                UrlCanonicalizer.canonicalize(r.url()), r.publishedAt());
    }

    private static ConferenceTalk canonicalize(ConferenceTalk t) {
        return new ConferenceTalk(t.title(), t.channelName(), t.conferenceName(),
                UrlCanonicalizer.canonicalize(t.url()), t.publishedAt(), t.viewCount());
    }

    private static DbEngineRanking canonicalize(DbEngineRanking d) {
        return new DbEngineRanking(d.dbName(), d.rank(), d.rankChange(), d.score(), d.scoreChange(),
                UrlCanonicalizer.canonicalize(d.url()), d.updatedAt());
    }

    private boolean isFromAuthority(Tweet tweet, Set<String> authorities) {
        if (authorities.isEmpty()) {
            return true;
        }
        String username = tweet.authorUsername().toLowerCase();
        return authorities.stream().anyMatch(auth -> auth.toLowerCase().equals(username));
    }

    private boolean isRecent(String createdAt, ZonedDateTime cutoff) {
        if (createdAt == null || createdAt.isBlank()) {
            return true;
        }
        try {
            ZonedDateTime tweetTime = ZonedDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME);
            return tweetTime.isAfter(cutoff);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isRelevant(Tweet tweet) {
        if (tweet.text() == null) {
            return false;
        }
        String lower = tweet.text().toLowerCase();
        return RELEVANCE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
