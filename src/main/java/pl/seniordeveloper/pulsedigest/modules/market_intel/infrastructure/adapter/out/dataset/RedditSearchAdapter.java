package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RedditPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedditSearchAdapter {

    private static final String BASE_URL = "https://www.reddit.com";

    private final ObjectMapper objectMapper;
    private final ReportProperties reportProperties;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "PulseDigest/1.0 tech-digest-bot")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<RedditPost> fetchTopPosts() {
        ReportProperties.RedditProperties cfg = reportProperties.reddit();
        List<RedditPost> result = new ArrayList<>();
        for (String subreddit : cfg.subreddits()) {
            result.addAll(fetchSubreddit(subreddit, cfg.limit(), cfg.minScore()));
        }
        log.info("Reddit łącznie: {} postów z {} subredditów", result.size(), cfg.subreddits().size());
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<RedditPost> fetchSubreddit(String subreddit, int limit, int minScore) {
        try {
            String json = restClient.get()
                    .uri("/r/{sub}/top.json?t=day&limit={limit}", subreddit, limit)
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                log.warn("Pusta odpowiedź dla r/{}", subreddit);
                return List.of();
            }

            RedditResponse response = objectMapper.readValue(json, RedditResponse.class);
            if (response.data() == null || response.data().children() == null) {
                return List.of();
            }

            List<RedditPost> posts = response.data().children().stream()
                    .map(child -> child.data())
                    .filter(d -> d.score() >= minScore)
                    .map(d -> new RedditPost(d.title(), resolveUrl(d), d.score(), subreddit))
                    .toList();

            log.info("r/{}: {} postów (min score={})", subreddit, posts.size(), minScore);
            return posts;

        } catch (Exception e) {
            log.warn("Błąd pobierania r/{}: {}", subreddit, e.getMessage());
            return List.of();
        }
    }

    /**
     * Posts with selftext have a Reddit URL; external links have a direct URL.
     */
    private String resolveUrl(PostData data) {
        if (data.url() != null && !data.url().contains("reddit.com/r/")) {
            return data.url();
        }
        return "https://www.reddit.com" + data.permalink();
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
