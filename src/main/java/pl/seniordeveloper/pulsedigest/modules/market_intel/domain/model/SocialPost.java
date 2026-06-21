package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * A post from a free social network (Bluesky / Mastodon), used to recover CV-relevant signal from
 * accounts that were trimmed off the X call budget. {@code network} ("Bluesky" / "Mastodon") doubles
 * as the source label in the digest.
 */
public record SocialPost(
        String network,
        String author,
        String text,
        String url,
        int likeCount
) {
}
