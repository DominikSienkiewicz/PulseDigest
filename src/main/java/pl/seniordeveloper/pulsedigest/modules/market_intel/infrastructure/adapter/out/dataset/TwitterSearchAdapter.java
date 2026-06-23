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
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Tweet;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ResearchProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class TwitterSearchAdapter {

    private static final String BASE_URL = "https://api.x.com/2";
    private static final DateTimeFormatter X_API_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final int ACCOUNT_BATCH_SIZE = 8;
    private static final int MAX_RESULTS = 30;
    private static final String SORT_RECENCY = "recency";
    private static final String SORT_RELEVANCY = "relevancy";

    private static final String ANTHROPIC_QUERY =
            "(from:AnthropicAI OR from:claudeai) "
                    + "(Claude OR model OR release OR announcement OR update) -is:retweet";

    private final ObjectMapper objectMapper;
    private final TwitterProperties twitterProperties;
    private final ResearchProperties researchProperties;
    private RestClient restClient;

    // Per-run X API call counter. The app is a single-shot batch (fresh JVM per run), so this counts
    // exactly one run's calls; once it exceeds twitter.max-calls-per-run, further fetches short-circuit.
    private final AtomicInteger callsThisRun = new AtomicInteger(0);

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + twitterProperties.bearerToken())
                .build();
    }

    /**
     * Pobiera tweety ze skonfigurowanych kont, pogrupowanych w batche po 8.
     */
    public List<Tweet> searchInfluencerTweets() {
        List<String> batchQueries = buildAccountBatchQueries(twitterProperties.accounts());
        List<Tweet> result = new ArrayList<>();
        for (String query : batchQueries) {
            result.addAll(fetchTweets(query, MAX_RESULTS, researchProperties.daysBack(), SORT_RECENCY));
        }
        log.info("Influencer tweets łącznie: {} (z {} batchy kont)", result.size(), batchQueries.size());
        return result;
    }

    /**
     * Wyszukuje tweety według skonfigurowanych zapytań tematycznych.
     */
    public List<Tweet> searchTopicTweets() {
        List<Tweet> result = new ArrayList<>();
        for (String query : twitterProperties.queries()) {
            result.addAll(fetchTweets(query, MAX_RESULTS, researchProperties.daysBack(), SORT_RELEVANCY));
        }
        log.info("Topic tweets łącznie: {} (z {} queries)", result.size(), twitterProperties.queries().size());
        return result;
    }

    /**
     * Pobiera tweety Anthropic/Claude – kontekst produktowy.
     */
    public List<Tweet> searchAnthropicTweets() {
        return fetchTweets(ANTHROPIC_QUERY, MAX_RESULTS, researchProperties.daysBack(), SORT_RECENCY);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<String> buildAccountBatchQueries(List<String> accounts) {
        List<String> batchQueries = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i += ACCOUNT_BATCH_SIZE) {
            List<String> batch = accounts.subList(i, Math.min(i + ACCOUNT_BATCH_SIZE, accounts.size()));
            String fromClause = batch.stream()
                    .map(a -> "from:" + a)
                    .collect(Collectors.joining(" OR "));
            batchQueries.add("(" + fromClause + ") -is:retweet");
        }
        return batchQueries;
    }

    // Appends the `min_faves:N` engagement operator to every query when twitter.min-faves > 0, so X
    // filters low-engagement tweets server-side (saving read quota) instead of us paying for them and
    // discarding client-side. Disabled (0) by default — not all X access tiers support the operator.
    private String appendMinFaves(String query) {
        int minFaves = twitterProperties.minFaves();
        return minFaves > 0 ? query + " min_faves:" + minFaves : query;
    }

    private List<Tweet> fetchTweets(String query, int maxResults, int daysBack, String sortOrder) {
        int callNo = callsThisRun.incrementAndGet();
        int budget = twitterProperties.maxCallsPerRun();
        if (callNo > budget) {
            log.warn("Budżet wywołań X wyczerpany ({} calli/run) — pomijam zapytanie: {}...",
                    budget, query.substring(0, Math.min(40, query.length())));
            return List.of();
        }

        String effectiveQuery = appendMinFaves(query);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String endTime = now.minusMinutes(10).format(X_API_FMT);
        String startTime = now.minusDays(daysBack).format(X_API_FMT);

        log.info("X API query [{} dni, sort={}, call {}/{}]: {}...",
                daysBack, sortOrder, callNo, budget,
                effectiveQuery.substring(0, Math.min(70, effectiveQuery.length())));

        // HTTP/transport errors (402 CreditsDepleted, 401, 429, 5xx) intentionally propagate.
        // MarketResearchService.fetchSource catches them and marks the source as FAILED in
        // SourceFetchReport, so a dead Twitter feed shows up in the digest health section
        // instead of silently returning zero items.
        String raw = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tweets/search/recent")
                        .queryParam("query", effectiveQuery)
                        .queryParam("max_results", Math.max(10, Math.min(maxResults, 100)))
                        .queryParam("tweet.fields", "created_at,author_id,text,public_metrics")
                        .queryParam("expansions", "author_id")
                        .queryParam("user.fields", "username,name")
                        .queryParam("start_time", startTime)
                        .queryParam("end_time", endTime)
                        .queryParam("sort_order", sortOrder)
                        .build())
                .retrieve()
                .body(String.class);

        return parseResponse(raw);
    }

    private List<Tweet> parseResponse(String json) {
        if (json == null || json.isBlank()) {
            log.warn("X API zwróciło pustą odpowiedź");
            return List.of();
        }
        try {
            TwitterApiResponse response = objectMapper.readValue(json, TwitterApiResponse.class);

            if (response.data() == null || response.data().isEmpty()) {
                logEmptyResponse(json);
                return List.of();
            }

            Map<String, TwitterApiResponse.UserData> usersById = buildUsersById(response);
            List<Tweet> result = new ArrayList<>();
            for (TwitterApiResponse.TweetData td : response.data()) {
                result.add(toTweet(td, usersById));
            }
            log.info("Sparsowano {} tweetów", result.size());
            return result;

        } catch (Exception e) {
            log.error("Błąd parsowania JSON z X API: {} | snippet: {}",
                    e.getMessage(), json.substring(0, Math.min(300, json.length())));
            return List.of();
        }
    }

    private void logEmptyResponse(String json) {
        if (json.contains("\"errors\"") || json.contains("\"error\"")) {
            log.warn("X API zwróciło błąd: {}", json.substring(0, Math.min(500, json.length())));
        } else {
            log.info("X API: brak wyników (data[] puste)");
        }
    }

    private static Map<String, TwitterApiResponse.UserData> buildUsersById(TwitterApiResponse response) {
        if (response.includes() == null) {
            return Map.of();
        }
        return response.includes().users().stream()
                .collect(Collectors.toMap(TwitterApiResponse.UserData::id, Function.identity(), (a, b) -> a));
    }

    private static Tweet toTweet(
            TwitterApiResponse.TweetData td, Map<String, TwitterApiResponse.UserData> usersById) {
        TwitterApiResponse.UserData user = usersById.get(td.authorId());
        int likes = td.publicMetrics() != null ? td.publicMetrics().likeCount() : 0;
        int rt = td.publicMetrics() != null ? td.publicMetrics().retweetCount() : 0;
        int reply = td.publicMetrics() != null ? td.publicMetrics().replyCount() : 0;
        return new Tweet(
                td.id(), td.text(),
                user != null ? user.username() : td.authorId(),
                user != null ? user.name() : "",
                td.createdAt(), likes, rt, reply);
    }

    // ── X API v2 response shape ───────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitterApiResponse(List<TweetData> data, Includes includes) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record TweetData(
                String id,
                String text,
                @JsonProperty("author_id") String authorId,
                @JsonProperty("created_at") String createdAt,
                @JsonProperty("public_metrics") PublicMetrics publicMetrics
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record PublicMetrics(
                @JsonProperty("retweet_count") int retweetCount,
                @JsonProperty("like_count") int likeCount,
                @JsonProperty("reply_count") int replyCount,
                @JsonProperty("quote_count") int quoteCount
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Includes(List<UserData> users) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UserData(String id, String name, String username) {
        }
    }
}
