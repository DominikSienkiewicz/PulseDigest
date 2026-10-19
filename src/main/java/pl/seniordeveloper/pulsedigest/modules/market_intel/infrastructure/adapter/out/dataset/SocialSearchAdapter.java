package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges Bluesky + Mastodon into a single "Social" source with per-network resilience: only a total
 * failure of BOTH networks propagates (so the source is marked FAILED only on a real outage). Applies
 * each network's own like floor, caps how much of the budget one account may take, and ranks the rest
 * by engagement.
 *
 * <p><strong>Why the per-account cap exists (K6).</strong> Selecting the top N by raw like count
 * across two networks hands the whole budget to whichever account is most popular in absolute terms,
 * and on Bluesky that was Bluesky's own account — 29 posts a fortnight at a median of 1403 likes,
 * against single digits everywhere else. The source was nominally "Bluesky + Mastodon, fifteen posts"
 * and was in fact one company account's timeline. Replacing the seed handle alone would not have
 * fixed that: the next popular account would have inherited the monopoly. The ranking had to change
 * too, which is why K6 is a bug and not a configuration preference.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SocialSearchAdapter {

    private static final String MASTODON = "Mastodon";

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
        List<SocialPost> eligible = all.stream()
                .filter(this::clearsItsNetworkFloor)
                .sorted(Comparator.comparingInt(SocialPost::likeCount).reversed())
                .toList();
        List<SocialPost> selected = capPerAccount(eligible);
        log.info("Social: {} fetched, {} cleared the floors (bluesky>={}, mastodon>={}), {} after the "
                        + "per-account cap of {} and the limit of {}",
                all.size(), eligible.size(), props.bluesky().minLikes(), props.mastodon().minLikes(),
                selected.size(), props.maxPerAccount(), props.limit());
        return selected;
    }

    private boolean clearsItsNetworkFloor(SocialPost post) {
        int floor = MASTODON.equalsIgnoreCase(post.network())
                ? props.mastodon().minLikes()
                : props.bluesky().minLikes();
        return post.likeCount() >= floor;
    }

    /**
     * Takes the ranked list in order, skipping any post from an account that has already filled its
     * quota. Order is preserved, so the best post of a prolific account still outranks a weaker one
     * elsewhere — the cap limits how many slots an account wins, not whether it wins the first.
     */
    private List<SocialPost> capPerAccount(List<SocialPost> ranked) {
        Map<String, Integer> taken = new HashMap<>();
        List<SocialPost> selected = new ArrayList<>();
        for (SocialPost post : ranked) {
            if (selected.size() == props.limit()) {
                break;
            }
            String account = post.author() != null ? post.author() : "";
            if (taken.merge(account, 1, Integer::sum) <= props.maxPerAccount()) {
                selected.add(post);
            }
        }
        return selected;
    }
}
