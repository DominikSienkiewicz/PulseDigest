package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.QuotaErrors;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.RedditProperties;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedditSearchAdapter {

    private static final String BASE_URL = "https://www.reddit.com";

    private final ObjectMapper objectMapper;
    private final RedditProperties cfg;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "PulseDigest/1.0 tech-digest-bot")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<RedditPost> fetchTopPosts() {
        List<RedditPost> result = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        for (String subreddit : cfg.subreddits()) {
            try {
                result.addAll(fetchSubreddit(subreddit, cfg.limit(), cfg.minScore()));
            } catch (Exception e) {
                QuotaErrors.rethrowIfQuota(e);
                failed++;
                lastError = e;
                log.warn("Błąd pobierania r/{}: {}", subreddit, e.getMessage());
            }
        }
        if (failed > 0 && failed == cfg.subreddits().size()) {
            throw new IllegalStateException(
                    "All " + failed + " Reddit subreddits failed; last error: "
                            + (lastError != null ? lastError.getMessage() : "unknown"),
                    lastError);
        }
        log.info("Reddit łącznie: {} postów z {} subredditów ({} udanych)",
                result.size(), cfg.subreddits().size(), cfg.subreddits().size() - failed);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<RedditPost> fetchSubreddit(String subreddit, int limit, int minScore) {
        // HTTP errors propagate; caller aggregates per-subreddit failures.
        String json = restClient.get()
                .uri("/r/{sub}/top.json?t=day&limit={limit}", subreddit, limit)
                .retrieve()
                .body(String.class);

        if (json == null || json.isBlank()) {
            log.warn("Pusta odpowiedź dla r/{}", subreddit);
            return List.of();
        }

        RedditResponse response;
        try {
            response = objectMapper.readValue(json, RedditResponse.class);
        } catch (Exception e) {
            log.warn("Reddit r/{} parse failed: {}", subreddit, e.getMessage());
            return List.of();
        }
        if (response.data() == null || response.data().children() == null) {
            return List.of();
        }

        List<RedditPost> posts = response.data().children().stream()
                .map(Child::data)
                .filter(d -> d.score() >= minScore)
                .map(d -> new RedditPost(d.title(), resolveUrl(d), d.score(), subreddit))
                .toList();

        log.info("r/{}: {} postów (min score={})", subreddit, posts.size(), minScore);
        return posts;
    }

    /**
     * Posts with selftext have a Reddit URL; external links have a direct URL.
     */
    private String resolveUrl(PostData data) {
        if (data.url() != null && !data.url().contains("reddit.com/r/")) {
            return data.url();
        }
        return BASE_URL + data.permalink();
    }

    // ── Reddit JSON response shape ────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RedditResponse(ListingData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListingData(List<Child> children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Child(PostData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PostData(
            String title,
            String url,
            String permalink,
            int score,
            @JsonProperty("selftext") String selfText
    ) {
    }
}
