package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

/**
 * Builds the two reader-targeted highlight blocks at the top of the digest: "Must-know" (the few
 * highest-score items, each with a one-sentence why-it-matters action line) and "Deals &amp; Tools"
 * (adoptable launches / releases / features / promotions). Extracted from {@code ReportEmailBuilder}
 * to keep that class within its size budget and to make the highlight logic independently testable.
 */
final class DigestHighlightBuilder {

    // Package-private: ReportEmailBuilder reads it to decide the ⚡ subject marker, so the bar for
    // "must-know" is defined exactly once.
    static final int MUST_KNOW_THRESHOLD = 7;
    private static final int MUST_KNOW_LIMIT = 5;
    private static final int DEALS_THRESHOLD = 6;
    private static final int DEALS_LIMIT = 5;
    // Item types that belong in the "Deals & Tools" block — things the reader can adopt or claim now.
    private static final Set<String> DEAL_TYPES = Set.of("LAUNCH", "RELEASE", "FEATURE", "PROMOTION");

    private DigestHighlightBuilder() {
    }

    // Hero block: the few highest-score items (strong+, capped), each with a one-sentence
    // "why it matters to you" action line, plus 👍/👎 feedback links when a receiver URL is configured.
    // The "if you read nothing else" section.
    static String buildMustKnowSection(List<DigestItem> items, Map<String, Signal> signalByUrl,
                                      FeedbackProperties feedback, String edition) {
        List<DigestItem> mustKnow = items.stream()
                .filter(i -> i.score() >= MUST_KNOW_THRESHOLD)
                .sorted(Comparator.comparingInt(DigestItem::score).reversed())
                .limit(MUST_KNOW_LIMIT)
                .toList();
        if (mustKnow.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fffbeb;border-bottom:1px solid #fde68a\">");
        sb.append("<h2 style=\"color:#92400e;font-size:15px;margin:0 0 12px\">")
                .append("&#9889; Must-know &mdash; przeczytaj, je&#347;li nic innego</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DigestItem item : mustKnow) {
            String why = item.whyItMatters() != null && !item.whyItMatters().isBlank()
                    ? "<div style=\"color:#78350f;font-size:13px;margin-top:3px\">"
                      + escapeHtml(item.whyItMatters()) + "</div>"
                    : "";
            sb.append("<li style=\"margin-bottom:12px\">")
                    .append("<a href=\"").append(safeHref(item.url()))
                    .append("\" style=\"color:#92400e;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(escapeHtml(item.title())).append("</a>")
                    .append(" <span style=\"color:#b45309;font-weight:700;font-size:12px\">")
                    .append(item.score()).append("/10</span>")
                    .append(FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback))
                    .append(why)
                    .append(ScoreExplanationBuilder.buildWhyYouSeeThis(signalByUrl.get(item.url())))
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    // "Deals & Tools" — actionable launches/releases/features/promotions the reader can use right now.
    static String buildDealsAndToolsSection(List<DigestItem> items, FeedbackProperties feedback,
                                           String edition) {
        List<DigestItem> deals = items.stream()
                .filter(i -> i.score() >= DEALS_THRESHOLD)
                .filter(i -> i.type() != null && DEAL_TYPES.contains(i.type()))
                .sorted(Comparator.comparingInt(DigestItem::score).reversed())
                .limit(DEALS_LIMIT)
                .toList();
        if (deals.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fdf4ff;border-bottom:1px solid #f5d0fe\">");
        sb.append("<h2 style=\"color:#86198f;font-size:15px;margin:0 0 12px\">")
                .append("&#128736;&#65039; Deals &amp; Tools</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (DigestItem item : deals) {
            String summaryPart = item.summary() != null && !item.summary().isBlank()
                    ? "<div style=\"color:#701a75;font-size:13px;margin-top:3px\">"
                      + escapeHtml(item.summary()) + "</div>"
                    : "";
            sb.append("<li style=\"margin-bottom:10px\">")
                    .append("<a href=\"").append(safeHref(item.url()))
                    .append("\" style=\"color:#a21caf;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(escapeHtml(item.title())).append("</a> ")
                    .append(typeBadge(item.type() != null ? item.type() : "OTHER"))
                    .append(FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback))
                    .append(summaryPart)
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }
}
