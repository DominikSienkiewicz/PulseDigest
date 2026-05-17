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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ResearchPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;
import pl.seniordeveloper.pulsedigest.shared.async.AsyncQualifiers;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class MarketResearchService {

    private static final Set<String> RELEVANCE_KEYWORDS = Set.of(
            "ai", "llm", "agent", "agentic", "model", "gpt", "claude", "gemini", "copilot",
            "openai", "anthropic", "mistral", "qwen", "deepseek", "llama",
            "ml", "machine learning", "inference", "fine-tuning", "embedding", "rag", "vector",
            "reasoning", "context", "token", "mcp", "langchain", "llamaindex", "vllm", "ollama",
            "pytorch", "huggingface", "pydantic", "transformers",
            "java", "jvm", "kotlin", "groovy", "scala", "spring", "quarkus", "micronaut",
            "graalvm", "gradle", "maven", "testcontainers", "hibernate", "jdk", "jep",
            "python", "fastapi",
            "kubernetes", "k8s", "container", "docker", "podman", "containerd", "oci",
            "cncf", "helm", "istio", "linkerd", "knative", "argocd", "flux", "cilium", "ebpf",
            "operator", "serverless", "edge", "opentelemetry", "observability", "service mesh",
            "platform engineering", "gitops",
            "azure", "aws", "cloud", "lambda", "cloud run",
            "coding", "engineer", "programming", "developer", "software",
            "architecture", "ddd", "event sourcing", "cqrs",
            "benchmark", "release", "api"
    );
    private static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(90);

    private final MarketIntelligencePort intelligencePort;
    private final ResearchPolicy researchPolicy;
    @Qualifier(AsyncQualifiers.DATA_FETCH_EXECUTOR)
    private final Executor taskExecutor;

    public ResearchResult fetchAndFilter() {
        log.info("Starting market research data gathering...");

        CompletableFuture<FetchOutcome<Tweet>> futureInfluencer =
                fetchSource("Twitter/X influencer", intelligencePort::fetchInfluencerTweets);
        CompletableFuture<FetchOutcome<Tweet>> futureTopic =
                fetchSource("Twitter/X topic", intelligencePort::fetchTopicTweets);
        CompletableFuture<FetchOutcome<Tweet>> futureAnthropic =
                fetchSource("Twitter/X Anthropic", intelligencePort::fetchAnthropicTweets);
        CompletableFuture<FetchOutcome<HackerNewsPost>> futureHn =
                fetchSource("Hacker News", intelligencePort::fetchTopDiscussions);
        CompletableFuture<FetchOutcome<GithubRepo>> futureGh =
                fetchSource("GitHub", intelligencePort::fetchTrendingRepos);
        CompletableFuture<FetchOutcome<RssItem>> futureRss =
                fetchSource("RSS", intelligencePort::fetchRssItems);
        CompletableFuture<FetchOutcome<RedditPost>> futureReddit =
                fetchSource("Reddit", intelligencePort::fetchRedditPosts);
        CompletableFuture<FetchOutcome<ResearchPaper>> futurePapers =
                fetchSource("arXiv", intelligencePort::fetchLatestPapers);
        CompletableFuture<FetchOutcome<SoftwareRelease>> futureReleases =
                fetchSource("GitHub Releases", intelligencePort::fetchLatestReleases);
        CompletableFuture<FetchOutcome<HuggingFaceModel>> futureHf =
                fetchSource("Hugging Face", intelligencePort::fetchTrendingModels);
        CompletableFuture<FetchOutcome<ProductHuntPost>> futurePh =
                fetchSource("Product Hunt", intelligencePort::fetchProductLaunches);
        CompletableFuture<FetchOutcome<SecurityAdvisory>> futureAdvisories =
                fetchSource("Security Advisories", intelligencePort::fetchSecurityAdvisories);
        CompletableFuture<FetchOutcome<NvdVulnerability>> futureNvd =
                fetchSource("NVD/CVE", intelligencePort::fetchNvdVulnerabilities);
        CompletableFuture<FetchOutcome<PackageTrend>> futurePackages =
                fetchSource("Libraries.io", intelligencePort::fetchPackageTrends);
        CompletableFuture<FetchOutcome<JepUpdate>> futureJep =
                fetchSource("OpenJDK JEP", intelligencePort::fetchJepUpdates);
        CompletableFuture<FetchOutcome<CncfProjectUpdate>> futureCncf =
                fetchSource("CNCF Landscape", intelligencePort::fetchCncfLandscapeChanges);
        CompletableFuture<FetchOutcome<RadarEntry>> futureRadar =
                fetchSource("Tech Radar", intelligencePort::fetchTechRadarEntries);
        CompletableFuture<FetchOutcome<ConferenceTalk>> futureTalks =
                fetchSource("Conference Talks", intelligencePort::fetchConferenceTalks);
        CompletableFuture<FetchOutcome<DbEngineRanking>> futureDb =
                fetchSource("DB-Engines", intelligencePort::fetchDbEngineRankings);

        CompletableFuture.allOf(futureInfluencer, futureTopic, futureAnthropic, futureHn, futureGh,
                futureRss, futureReddit, futurePapers, futureReleases,
                futureHf, futurePh, futureAdvisories, futureNvd, futurePackages,
                futureJep, futureCncf, futureRadar, futureTalks, futureDb).join();

        FetchOutcome<Tweet> influencer = futureInfluencer.join();
        FetchOutcome<Tweet> topic = futureTopic.join();
        FetchOutcome<Tweet> anthropic = futureAnthropic.join();
        FetchOutcome<HackerNewsPost> hn = futureHn.join();
        FetchOutcome<GithubRepo> gh = futureGh.join();
        FetchOutcome<RssItem> rss = futureRss.join();
        FetchOutcome<RedditPost> reddit = futureReddit.join();
        FetchOutcome<ResearchPaper> papers = futurePapers.join();
        FetchOutcome<SoftwareRelease> releases = futureReleases.join();
        FetchOutcome<HuggingFaceModel> hf = futureHf.join();
        FetchOutcome<ProductHuntPost> ph = futurePh.join();
        FetchOutcome<SecurityAdvisory> advisories = futureAdvisories.join();
        FetchOutcome<NvdVulnerability> nvd = futureNvd.join();
        FetchOutcome<PackageTrend> packages = futurePackages.join();
        FetchOutcome<JepUpdate> jep = futureJep.join();
        FetchOutcome<CncfProjectUpdate> cncf = futureCncf.join();
        FetchOutcome<RadarEntry> radar = futureRadar.join();
        FetchOutcome<ConferenceTalk> talks = futureTalks.join();
        FetchOutcome<DbEngineRanking> db = futureDb.join();

        List<Tweet> rawInfluencer = influencer.items();
        List<Tweet> rawTopic = topic.items();
        List<Tweet> rawAnthropic = anthropic.items();
        List<HackerNewsPost> rawHn = hn.items();
        List<GithubRepo> rawGh = gh.items();
        List<RssItem> rawRss = rss.items();
        List<RedditPost> rawReddit = reddit.items();
        List<ResearchPaper> rawPapers = papers.items();
        List<SoftwareRelease> rawReleases = releases.items();
        List<HuggingFaceModel> rawHf = hf.items();
        List<ProductHuntPost> rawPh = ph.items();
        List<SecurityAdvisory> rawAdvisories = advisories.items();
        List<NvdVulnerability> rawNvd = nvd.items();
        List<PackageTrend> rawPackages = packages.items();
        List<JepUpdate> rawJep = jep.items();
        List<CncfProjectUpdate> rawCncf = cncf.items();
        List<RadarEntry> rawRadar = radar.items();
        List<ConferenceTalk> rawTalks = talks.items();
        List<DbEngineRanking> rawDb = db.items();
        List<SourceFetchReport> sourceFetchReports = Stream.of(
                        influencer.report(), topic.report(), anthropic.report(), hn.report(), gh.report(),
                        rss.report(), reddit.report(), papers.report(), releases.report(), hf.report(), ph.report(),
                        advisories.report(), nvd.report(), packages.report(), jep.report(), cncf.report(),
                        radar.report(), talks.report(), db.report())
                .toList();

        Set<String> authorityUsernames = Set.copyOf(researchPolicy.authorityUsernames());
        int minLikes = researchPolicy.minLikes();
        int daysBack = researchPolicy.daysBack();
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
                rawHn.size(), rawGh.size(), rawRss.size(), rawReddit.size(),
                sourceFetchReports
        );
    }

    private <T> CompletableFuture<FetchOutcome<T>> fetchSource(String sourceName, Supplier<List<T>> supplier) {
        Instant start = Instant.now();
        return CompletableFuture.supplyAsync(supplier, taskExecutor)
                .orTimeout(SOURCE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .handle((items, throwable) -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    if (throwable != null) {
                        String message = rootMessage(throwable);
                        log.warn("{} fetch failed after {}ms: {}", sourceName, elapsed, message);
                        return new FetchOutcome<>(
                                List.of(),
                                SourceFetchReport.failed(sourceName, elapsed, message));
                    }
                    List<T> safeItems = items != null ? items : List.of();
                    return new FetchOutcome<>(
                            safeItems,
                            SourceFetchReport.success(sourceName, safeItems.size(), elapsed));
                });
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private record FetchOutcome<T>(List<T> items, SourceFetchReport report) {
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
