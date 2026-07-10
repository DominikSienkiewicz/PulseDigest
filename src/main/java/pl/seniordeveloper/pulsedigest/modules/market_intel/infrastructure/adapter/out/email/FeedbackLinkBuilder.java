package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.util.HmacSigner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * Renders the 👍/👎 links that point at the external feedback receiver — the headless batch never
 * serves HTTP itself.
 *
 * <p>Each link carries the edition it was sent in, so the receiver can enforce one vote per item per
 * edition and a mail scanner prefetching the same link cannot amplify it. When a signing secret is
 * configured, the link also carries an HMAC over {@code url|vote|source|edition}: that does not stop
 * a scanner from following the link, but it does mean nobody can forge a vote or flip an existing
 * one. Without a secret the links render exactly as before, so signing can ship ahead of the
 * receiver that verifies it.
 */
final class FeedbackLinkBuilder {

    private static final String VOTE_UP = "up";
    private static final String VOTE_DOWN = "down";

    private FeedbackLinkBuilder() {
    }

    /** Empty when no receiver is configured — the digest then simply carries no feedback links. */
    static String render(String itemUrl, String source, String edition, FeedbackProperties feedback) {
        if (feedback.receiverUrl() == null || feedback.receiverUrl().isBlank()) {
            return "";
        }
        return thumb(voteUrl(itemUrl, source, edition, VOTE_UP, feedback), "&#128077;")
                + thumb(voteUrl(itemUrl, source, edition, VOTE_DOWN, feedback), "&#128078;");
    }

    private static String thumb(String href, String glyph) {
        return " <a href=\"" + safeHref(href) + "\" style=\"text-decoration:none;font-size:12px\">"
                + glyph + "</a>";
    }

    // The signature is computed over the raw values the reader is voting on, then encoded alongside
    // them — signing the encoded form would make the receiver's verification depend on our encoder.
    private static String voteUrl(String itemUrl, String source, String edition,
                                  String vote, FeedbackProperties feedback) {
        String rawUrl = itemUrl != null ? itemUrl : "";
        String rawSource = source != null ? source : "";
        String query = feedback.receiverUrl()
                + "?url=" + encode(rawUrl)
                + "&vote=" + vote
                + "&source=" + encode(rawSource)
                + "&edition=" + encode(edition);
        if (!feedback.signingEnabled()) {
            return query;
        }
        String canonical = rawUrl + "|" + vote + "|" + rawSource + "|" + edition;
        return query + "&sig=" + encode(HmacSigner.sign(canonical, feedback.signingSecret()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
