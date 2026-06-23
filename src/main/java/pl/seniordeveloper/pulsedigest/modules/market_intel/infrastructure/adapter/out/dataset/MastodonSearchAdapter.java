package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches recent posts from configured Mastodon hashtag timelines on a single instance via the public
 * API (no auth). Per-hashtag resilience: a failing tag is skipped; only a total failure propagates.
 * Status content is HTML, so it is stripped to plain text.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MastodonSearchAdapter {

    private static final String NETWORK = "Mastodon";

    private final ObjectMapper objectMapper;
    private final SocialProperties props;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<SocialPost> fetchPosts(int perTagLimit) {
        List<String> hashtags = props.mastodon().hashtags();
        if (hashtags.isEmpty()) {
            return List.of();
        }
        List<SocialPost> result = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        for (String tag : hashtags) {
            try {
                result.addAll(fetchTag(tag, perTagLimit));
            } catch (Exception e) {
                failed++;
                lastError = e;
                log.warn("Mastodon tag '{}' fetch failed: {}", tag, e.getMessage());
            }
        }
        if (failed == hashtags.size()) {
            throw new IllegalStateException("All " + failed + " Mastodon tag fetches failed; last: "
                    + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
        }
        return result;
    }

    private List<SocialPost> fetchTag(String tag, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(props.mastodon().instanceUrl())
                .path("/api/v1/timelines/tag/" + tag)
                .queryParam("limit", Math.clamp(limit, 1, 40))
                .build()
                .toUri();
        String json = restClient.get().uri(uri).retrieve().body(String.class);
        return parse(json);
    }

    List<SocialPost> parse(String json) {
        try {
            List<Status> statuses = objectMapper.readValue(json, new TypeReference<List<Status>>() {
            });
            List<SocialPost> posts = new ArrayList<>();
            for (Status s : statuses) {
                if (s.url() == null) {
                    continue;
                }
                String author = s.account() != null ? s.account().acct() : "";
                posts.add(new SocialPost(NETWORK, author, stripHtml(s.content()), s.url(), s.favouritesCount()));
            }
            return posts;
        } catch (Exception e) {
            log.warn("Mastodon parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Status(
            String url,
            String content,
            Account account,
            @JsonProperty("favourites_count") int favouritesCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Account(String acct, @JsonProperty("display_name") String displayName) {
    }
}
