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

    private SocialSearchAdapter adapter(int limit, int minLikes) {
        SocialProperties props = new SocialProperties(limit, minLikes,
                new SocialProperties.Bluesky("http://x", List.of()),
                new SocialProperties.Mastodon("http://x", List.of()));
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
    void survivesWhenOneNetworkFails() {
        when(bluesky.fetchPosts(anyInt())).thenThrow(new RuntimeException("bsky down"));
        when(mastodon.fetchPosts(anyInt())).thenReturn(List.of(new SocialPost("Mastodon", "c", "t", "u", 30)));

        assertThat(adapter(10, 0).fetchSocialPosts()).hasSize(1);
    }

    @Test
    void throwsWhenBothNetworksFail() {
        when(bluesky.fetchPosts(anyInt())).thenThrow(new RuntimeException("bsky down"));
        when(mastodon.fetchPosts(anyInt())).thenThrow(new RuntimeException("mast down"));

        assertThatThrownBy(() -> adapter(10, 0).fetchSocialPosts())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Both social networks failed");
    }
}
