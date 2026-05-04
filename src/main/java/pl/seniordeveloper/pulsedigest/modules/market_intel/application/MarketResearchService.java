package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.GithubRepo;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HackerNewsPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RssItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SoftwareRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.MarketIntelligencePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

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

        CompletableFuture.allOf(futureInfluencer, futureTopic, futureAnthropic, futureHn, futureGh,
                futureRss, futureReddit, futurePapers, futureReleases).join();

        List<Tweet> rawInfluencer = futureInfluencer.join();
        List<Tweet> rawTopic = futureTopic.join();
        List<Tweet> rawAnthropic = futureAnthropic.join();
        List<HackerNewsPost> rawHn = futureHn.join();
        List<GithubRepo> rawGh = futureGh.join();
        List<RssItem> rawRss = futureRss.join();
        List<RedditPost> rawReddit = futureReddit.join();
        List<ResearchPaper> rawPapers = futurePapers.join();
        List<SoftwareRelease> rawReleases = futureReleases.join();

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
                .limit(25)
                .toList();

        return new ResearchResult(
                finalTweets, rawHn, rawGh, rawRss, rawReddit, rawPapers, rawReleases,
                LocalDateTime.now(),
                rawInfluencer.size() + rawTopic.size() + rawAnthropic.size(),
                rawHn.size(), rawGh.size(), rawRss.size(), rawReddit.size()
        );
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
