package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Free-source recovery (C7): Bluesky author feeds + Mastodon hashtag timelines, both public (no auth),
 * recovering CV-relevant signal cut off the X budget. One merged "Social" source — the network name is
 * carried on each {@code SocialPost} as its source label.
 *
 * <p><strong>The like floor is per network, and that is not symmetry for its own sake (K6).</strong>
 * Measured live on 2026-10-19: across 200 posts in five fosstodon hashtag timelines, exactly one
 * cleared five favourites, while the Bluesky seed account posted 29 times in a fortnight at a median
 * of 1403 likes. One shared floor of 5 therefore meant the Mastodon half contributed nothing and the
 * loudest Bluesky account contributed everything. Engagement on these two networks is not the same
 * quantity and cannot share a threshold.
 */
@ConfigurationProperties(prefix = "report.social")
@Validated
public record SocialProperties(
        @Min(1) int limit,
        @Min(1) int maxPerAccount,
        @NotNull @Valid Bluesky bluesky,
        @NotNull @Valid Mastodon mastodon
) {

    /** Bluesky AppView (public): author feeds for the configured handles. */
    public record Bluesky(@NotBlank String baseUrl, @Min(0) int minLikes, List<@NotBlank String> handles) {
        public Bluesky {
            handles = handles != null ? List.copyOf(handles) : List.of();
        }
    }

    /** Mastodon (public): hashtag timelines on a single instance. */
    public record Mastodon(@NotBlank String instanceUrl, @Min(0) int minLikes, List<@NotBlank String> hashtags) {
        public Mastodon {
            hashtags = hashtags != null ? List.copyOf(hashtags) : List.of();
        }
    }
}
