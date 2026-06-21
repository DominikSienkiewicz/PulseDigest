package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Merges Bluesky + Mastodon into a single "Social" source with per-network resilience: only a total
 * failure of BOTH networks propagates (so the source is marked FAILED only on a real outage). Applies
 * the min-likes floor and the overall limit, ranking by engagement.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SocialSearchAdapter {

    private final BlueskySearchAdapter blueskySearchAdapter;
    private final MastodonSearchAdapter mastodonSearchAdapter;
    private final SocialProperties props;

    public List<SocialPost> fetchSocialPosts() {
        int perAccountLimit = Math.max(1, props.limit());
        List<SocialPost> all = new ArrayList<>();
        int failed = 0;
        Exception lastError = null;
        try {
            all.addAll(blueskySearchAdapter.fetchPosts(perAccountLimit));
        } catch (Exception e) {
            failed++;
            lastError = e;
            log.warn("Bluesky source failed: {}", e.getMessage());
        }
        try {
            all.addAll(mastodonSearchAdapter.fetchPosts(perAccountLimit));
        } catch (Exception e) {
            failed++;
            lastError = e;
            log.warn("Mastodon source failed: {}", e.getMessage());
        }
        if (failed == 2) {
            throw new IllegalStateException("Both social networks failed; last: "
                    + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
        }
        List<SocialPost> selected = all.stream()
                .filter(p -> p.likeCount() >= props.minLikes())
                .sorted(Comparator.comparingInt(SocialPost::likeCount).reversed())
                .limit(props.limit())
                .toList();
        log.info("Social: {} posts ({} after min-likes>={} + limit {})",
                all.size(), selected.size(), props.minLikes(), props.limit());
        return selected;
    }
}
