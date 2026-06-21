package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Fetches recent posts from configured Bluesky handles via the public AppView author-feed API
 * (no auth). Per-handle resilience: a failing handle is skipped; only a total failure propagates.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BlueskySearchAdapter {

    private static final String NETWORK = "Bluesky";

    private final ObjectMapper objectMapper;
    private final SocialProperties props;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<SocialPost> fetchPosts(int perHandleLimit) {
        List<String> handles = props.bluesky().handles();
        if (handles.isEmpty()) {
            return List.of();
        }
        List<SocialPost> result = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        for (String handle : handles) {
            try {
                result.addAll(fetchHandle(handle, perHandleLimit));
            } catch (Exception e) {
                failed++;
                lastError = e;
                log.warn("Bluesky handle '{}' fetch failed: {}", handle, e.getMessage());
            }
        }
        if (failed == handles.size()) {
            throw new IllegalStateException("All " + failed + " Bluesky handle fetches failed; last: "
                    + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
        }
        return result;
    }

    private List<SocialPost> fetchHandle(String handle, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(props.bluesky().baseUrl())
                .path("/xrpc/app.bsky.feed.getAuthorFeed")
                .queryParam("actor", handle)
                .queryParam("limit", Math.max(1, Math.min(limit, 100)))
                .build()
                .toUri();
        String json = restClient.get().uri(uri).retrieve().body(String.class);
        return parse(json);
    }

    List<SocialPost> parse(String json) {
        try {
            FeedResponse response = objectMapper.readValue(json, FeedResponse.class);
            if (response.feed() == null) {
                return List.of();
            }
            List<SocialPost> posts = new ArrayList<>();
            for (FeedItem item : response.feed()) {
                Post p = item.post();
                if (p == null || p.author() == null || p.postRecord() == null) {
                    continue;
                }
                String handle = p.author().handle();
                posts.add(new SocialPost(NETWORK, handle, p.postRecord().text(),
                        toWebUrl(handle, p.uri()), p.likeCount()));
            }
            return posts;
        } catch (Exception e) {
            log.warn("Bluesky parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    // at://did:plc:xxx/app.bsky.feed.post/{rkey} -> https://bsky.app/profile/{handle}/post/{rkey}
    private String toWebUrl(String handle, String atUri) {
        if (atUri == null || handle == null) {
            return "";
        }
        int slash = atUri.lastIndexOf('/');
        String rkey = slash >= 0 ? atUri.substring(slash + 1) : atUri;
        return "https://bsky.app/profile/" + handle + "/post/" + rkey;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FeedResponse(List<FeedItem> feed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FeedItem(Post post) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Post(
            String uri,
            Author author,
            @JsonProperty("record") PostRecord postRecord,
            @JsonProperty("likeCount") int likeCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Author(String handle, String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PostRecord(String text) {
    }
}
