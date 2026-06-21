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
 */
@ConfigurationProperties(prefix = "report.social")
@Validated
public record SocialProperties(
        @Min(1) int limit,
        @Min(0) int minLikes,
        @NotNull @Valid Bluesky bluesky,
        @NotNull @Valid Mastodon mastodon
) {

    /** Bluesky AppView (public): author feeds for the configured handles. */
    public record Bluesky(@NotBlank String baseUrl, List<@NotBlank String> handles) {
        public Bluesky {
            handles = handles != null ? List.copyOf(handles) : List.of();
        }
    }

    /** Mastodon (public): hashtag timelines on a single instance. */
    public record Mastodon(@NotBlank String instanceUrl, List<@NotBlank String> hashtags) {
        public Mastodon {
            hashtags = hashtags != null ? List.copyOf(hashtags) : List.of();
        }
    }
}
