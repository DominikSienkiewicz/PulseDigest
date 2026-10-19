package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;

import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.confirmationLine;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

/**
 * Builds the two reader-targeted highlight blocks: "Must-know" (the highest-scoring stories, each with
 * a one-sentence why-it-matters action line) and "Deals &amp; Tools" (adoptable launches, releases,
 * features and promotions the reader has not already met further up).
 *
 * <p>Neither method decides membership any more — {@link DigestLayout} does, once, so a story cannot
 * land in both. These are renderers.
 *
 * <p>Both carry the radar's 🟠 candidate marker. It used to live only on table rows, which was fine
 * while every item also appeared in a table; now that a story renders in exactly one section, a marker
 * confined to the tables would simply never be seen on the stories that matter most.
 */
final class DigestHighlightBuilder {

    private static final String MUST_KNOW_TEXT = "#78350f";
    private static final String DEALS_TEXT = "#701a75";

    private DigestHighlightBuilder() {
    }

    // Hero block: the highest-scoring stories, each with a one-sentence "why it matters to you" action
    // line, plus 👍/👎 feedback links when a receiver URL is configured.
    static String buildMustKnowSection(List<DigestStory> stories, FeedbackProperties feedback,
                                       String edition, boolean explainScores) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fffbeb;border-bottom:1px solid #fde68a\">");
        sb.append("<h2 style=\"color:#92400e;font-size:15px;margin:0 0 12px\">")
                .append("&#9889; Must-know</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DigestStory story : stories) {
            DigestItem item = story.lead();
            String why = item.whyItMatters() != null && !item.whyItMatters().isBlank()
                    ? "<div style=\"color:" + MUST_KNOW_TEXT + ";font-size:13px;margin-top:3px\">"
                      + escapeHtml(item.whyItMatters()) + "</div>"
                    : "";
            sb.append("<li style=\"margin-bottom:12px\">")
                    .append("<a href=\"").append(safeHref(item.url()))
                    .append("\" style=\"color:#92400e;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(TrendBadgeBuilder.candidateMarker(story.signal()))
                    .append(escapeHtml(item.title())).append("</a>")
                    .append(" <span style=\"color:#b45309;font-weight:700;font-size:12px\">")
                    .append(item.score()).append("/10</span>")
                    .append(FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback))
                    .append(why)
                    .append(StackMarkerBuilder.buildStackMarker(story))
                    .append(confirmationLine(story.corroborations(), "#a16207"))
                    .append(TrendBadgeBuilder.buildRecurrenceBadge(story.signal()))
                    .append(explainScores ? ScoreExplanationBuilder.buildWhyYouSeeThis(story.signal()) : "")
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    // "Deals & Tools" — adoptable launches/releases/features/promotions the reader can use right now,
    // minus anything already shown in Critical trends or Must-know.
    static String buildDealsAndToolsSection(List<DigestStory> stories, FeedbackProperties feedback,
                                            String edition) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fdf4ff;border-bottom:1px solid #f5d0fe\">");
        sb.append("<h2 style=\"color:#86198f;font-size:15px;margin:0 0 12px\">")
                .append("&#128736;&#65039; Deals &amp; Tools</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DigestStory story : stories) {
            DigestItem item = story.lead();
            String summaryPart = item.summary() != null && !item.summary().isBlank()
                    ? "<div style=\"color:" + DEALS_TEXT + ";font-size:13px;margin-top:3px\">"
                      + escapeHtml(item.summary()) + "</div>"
                    : "";
            sb.append("<li style=\"margin-bottom:10px\">")
                    .append("<a href=\"").append(safeHref(item.url()))
                    .append("\" style=\"color:#a21caf;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(TrendBadgeBuilder.candidateMarker(story.signal()))
                    .append(escapeHtml(item.title())).append("</a> ")
                    .append(typeBadge(item.type() != null ? item.type() : "OTHER"))
                    .append(FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback))
                    .append(summaryPart)
                    .append(StackMarkerBuilder.buildStackMarker(story))
                    .append(confirmationLine(story.corroborations(), "#a855f7"))
                    .append(TrendBadgeBuilder.buildRecurrenceBadge(story.signal()))
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }
}
