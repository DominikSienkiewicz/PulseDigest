package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SocialPost;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.SocialProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialSearchAdapterTest {

    private final BlueskySearchAdapter bluesky = mock(BlueskySearchAdapter.class);
    private final MastodonSearchAdapter mastodon = mock(MastodonSearchAdapter.class);

    /** Per-account cap wide open, so the pre-K6 assertions below mean what they always meant. */
    private SocialSearchAdapter adapter(int limit, int minLikes) {
        return adapter(limit, minLikes, minLikes, Integer.MAX_VALUE);
    }

    private SocialSearchAdapter adapter(int limit, int blueskyFloor, int mastodonFloor, int maxPerAccount) {
        SocialProperties props = new SocialProperties(limit, maxPerAccount,
                new SocialProperties.Bluesky("http://x", blueskyFloor, List.of()),
                new SocialProperties.Mastodon("http://x", mastodonFloor, List.of()));
        return new SocialSearchAdapter(bluesky, mastodon, props);
    }

    @Test
    void mergesFiltersByMinLikesSortsAndLimits() {
        when(bluesky.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Bluesky", "a", "t1", "u1", 50),
                new SocialPost("Bluesky", "b", "t2", "u2", 2)));   // below min-likes
        when(mastodon.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Mastodon", "c", "t3", "u3", 30)));

        List<SocialPost> result = adapter(2, 5).fetchSocialPosts();

        assertThat(result).extracting(SocialPost::likeCount).containsExactly(50, 30);
    }

    @Test
    void oneLoudAccountCannotTakeTheWholeBudget() {
        // K6, as it actually behaved in production: the Bluesky company account posted at a median of
        // 1403 likes while everything relevant sat in single digits, and selecting the top N by raw
        // like count gave it every slot. The fix is the cap, not a different seed handle — without it
        // the next popular account simply inherits the monopoly.
        when(bluesky.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Bluesky", "bsky.app", "about bluesky", "u1", 1403),
                new SocialPost("Bluesky", "bsky.app", "also bluesky", "u2", 1200),
                new SocialPost("Bluesky", "bsky.app", "still bluesky", "u3", 1100),
                new SocialPost("Bluesky", "bsky.app", "yet more", "u4", 1000),
                new SocialPost("Bluesky", "nipafx.dev", "about the JVM", "u5", 7)));
        when(mastodon.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Mastodon", "someone@fosstodon.org", "about quarkus", "u6", 2)));

        List<SocialPost> result = adapter(5, 5, 1, 2).fetchSocialPosts();

        assertThat(result).extracting(SocialPost::author)
                .as("the loud account keeps its two best posts and no more; the quiet sources get in")
                .containsExactly("bsky.app", "bsky.app", "nipafx.dev", "someone@fosstodon.org");
    }

    @Test
    void eachNetworkIsJudgedAgainstItsOwnFloor() {
        // 200 posts across five fosstodon hashtags produced exactly one with five favourites, so a
        // shared floor of 5 was a decision to ignore Mastodon. Two favourites is a strong post there
        // and noise on Bluesky; the floors say so.
        when(bluesky.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Bluesky", "quiet.bsky.social", "ignored", "u1", 2)));
        when(mastodon.fetchPosts(anyInt())).thenReturn(List.of(
                new SocialPost("Mastodon", "someone@fosstodon.org", "kept", "u2", 2)));

        List<SocialPost> result = adapter(10, 5, 1, 3).fetchSocialPosts();

        assertThat(result).extracting(SocialPost::url).containsExactly("u2");
    }

    @Test
    void survivesWhenOneNetworkFails() {
        when(bluesky.fetchPosts(anyInt())).thenThrow(new RuntimeException("bsky down"));
        when(mastodon.fetchPosts(anyInt())).thenReturn(List.of(new SocialPost("Mastodon", "c", "t", "u", 30)));

        assertThat(adapter(10, 0).fetchSocialPosts()).hasSize(1);
    }

    @Test
    void throwsWhenBothNetworksFail() {
        when(bluesky.fetchPosts(anyInt())).thenThrow(new RuntimeException("bsky down"));
        when(mastodon.fetchPosts(anyInt())).thenThrow(new RuntimeException("mast down"));

        var adapter = adapter(10, 0);
        assertThatThrownBy(adapter::fetchSocialPosts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Both social networks failed");
    }
}
