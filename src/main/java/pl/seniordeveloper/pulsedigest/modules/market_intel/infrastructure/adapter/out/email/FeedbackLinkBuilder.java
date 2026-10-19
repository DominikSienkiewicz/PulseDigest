package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.util.HmacSigner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * Renders the 👍/👎/„zapisz" links that point at the external feedback receiver — the headless batch never
 * serves HTTP itself.
 *
 * <p>Each link carries the edition it was sent in, so the receiver can enforce one vote per item per
 * edition and a mail scanner prefetching the same link cannot amplify it. It also carries the item's
 * category, so a down-vote can punish the topic rather than the whole source. When a signing secret
 * is configured, the link carries an HMAC over {@code url|vote|source|category|edition}: that does not stop
 * a scanner from following the link, but it does mean nobody can forge a vote or flip an existing
 * one. Without a secret the links render exactly as before, so signing can ship ahead of the
 * receiver that verifies it.
 *
 * <p>The third link (N05) exists to make the first two mean something. With only 👍/👎 on offer, a
 * reader who merely wanted to keep an item for later had to click 👍 — the only button that did not
 * punish — and every such click entered the scorer as a source preference nobody had expressed.
 * {@code save} is recorded and counted as zero, so bookmarking stops contaminating the signal the
 * scorer learns from.
 *
 * <p>All three links still share one row per item per edition, by the unique index V1 created. A
 * reader who clicks 👍 and then 🔖 keeps only the first, which is the price of the property that
 * matters more: a mail scanner prefetching all three links cannot write three rows.
 */
final class FeedbackLinkBuilder {

    private static final String VOTE_UP = "up";
    private static final String VOTE_DOWN = "down";
    private static final String VOTE_SAVE = "save";

    private FeedbackLinkBuilder() {
    }

    /** Empty when no receiver is configured — the digest then simply carries no feedback links. */
    static String render(String itemUrl, String source, String category, String edition,
                         FeedbackProperties feedback) {
        if (feedback.receiverUrl() == null || feedback.receiverUrl().isBlank()) {
            return "";
        }
        return thumb(voteUrl(itemUrl, source, category, edition, VOTE_UP, feedback),
                        "&#128077;", "Wi\u0119cej takich")
                + thumb(voteUrl(itemUrl, source, category, edition, VOTE_DOWN, feedback),
                        "&#128078;", "Mniej takich")
                + save(voteUrl(itemUrl, source, category, edition, VOTE_SAVE, feedback));
    }

    /**
     * Spelled out rather than given a third glyph. Two thumbs read instantly because the gesture is
     * universal; a bookmark emoji beside them is a pale shape whose meaning the reader has to guess,
     * and a button nobody understands collects no signal — which would defeat the whole point of
     * offering it.
     */
    private static String save(String href) {
        return " <a href=\"" + safeHref(href) + "\" title=\"Zapisz na p\u00f3\u017aniej\""
                + " style=\"text-decoration:none;font-size:11px;color:#6b7280\">zapisz</a>";
    }

    /**
     * A link whose entire text is an emoji has no accessible name: a screen reader announces "link,
     * thumbs up sign", and a client that does not render emoji announces "link" and nothing else
     * (N16). {@code title} and {@code aria-label} both carry the name, because mail clients honour
     * one or the other and rarely document which.
     */
    private static String thumb(String href, String glyph, String label) {
        return " <a href=\"" + safeHref(href) + "\" title=\"" + label + "\" aria-label=\"" + label
                + "\" style=\"text-decoration:none;font-size:12px\">" + glyph + "</a>";
    }

    // The signature is computed over the raw values the reader is voting on, then encoded alongside
    // them — signing the encoded form would make the receiver's verification depend on our encoder.
    private static String voteUrl(String itemUrl, String source, String category, String edition,
                                  String vote, FeedbackProperties feedback) {
        String rawUrl = itemUrl != null ? itemUrl : "";
        String rawSource = source != null ? source : "";
        String rawCategory = category != null ? category : "";
        String query = feedback.receiverUrl()
                + "?url=" + encode(rawUrl)
                + "&vote=" + vote
                + "&source=" + encode(rawSource)
                + "&category=" + encode(rawCategory)
                + "&edition=" + encode(edition);
        if (!feedback.signingEnabled()) {
            return query;
        }
        // Everything that changes what the receiver writes belongs inside the signature — including
        // the category, or a vote could be re-aimed at a topic the reader never voted on.
        String canonical = rawUrl + "|" + vote + "|" + rawSource + "|" + rawCategory + "|" + edition;
        return query + "&sig=" + encode(HmacSigner.sign(canonical, feedback.signingSecret()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
