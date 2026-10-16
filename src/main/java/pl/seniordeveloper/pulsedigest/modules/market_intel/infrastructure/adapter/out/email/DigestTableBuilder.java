package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;

import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.confirmationLine;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.formatEngagement;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

/**
 * Renders the two tiered tables — ⭐ Top picks and 🔌 Signals — which share the same columns and differ
 * only in header style and row background.
 *
 * <p>Both list what {@link DigestLayout} did not already place in a block above them, in the order the
 * model emitted it, so the tables read as an index of the remainder rather than a second pass over
 * material the reader has met.
 */
final class DigestTableBuilder {

    private static final String CLOSE_DIV = "</div>";

    private DigestTableBuilder() {
    }

    static String buildItemsSection(List<DigestStory> topPicks, List<DigestStory> signals,
                                    FeedbackProperties feedback, String edition) {
        return buildTopPicksSection(topPicks, feedback, edition)
                + buildMidTierSection(signals, feedback, edition);
    }

    private static String buildTopPicksSection(List<DigestStory> stories, FeedbackProperties feedback,
                                               String edition) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append("&#11088; Top picks (").append(stories.size()).append(")</h2>");
        sb.append(tableHead("#f9fafb", "14px"));
        for (DigestStory story : stories) {
            sb.append(buildRow(story, "", feedback, edition));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // Renders the "Signals" email section; named mid-tier to avoid collision with the Signal domain type.
    private static String buildMidTierSection(List<DigestStory> stories, FeedbackProperties feedback,
                                              String edition) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fafafa;border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#374151;font-size:15px;margin:0 0 12px\">")
                .append("&#128268; Signals (").append(stories.size()).append(")</h2>");
        sb.append(tableHead("#f3f4f6", "13px"));
        for (DigestStory story : stories) {
            sb.append(buildRow(story, "background:#fafafa", feedback, edition));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String tableHead(String headerBg, String fontSize) {
        return "<table style=\"width:100%;border-collapse:collapse;font-size:" + fontSize + "\">"
                + "<thead><tr style=\"background:" + headerBg + "\">"
                + th("Artyku&#322;") + th("Kategoria") + th("Typ") + th("&#377;r&oacute;d&#322;o") + th("Score")
                + "</tr></thead><tbody>";
    }

    private static String scoreColor(int score) {
        if (score >= 7) {
            return "#16a34a";
        }
        if (score >= 4) {
            return "#ca8a04";
        }
        return "#dc2626";
    }

    private static String buildRow(DigestStory story, String rowStyle, FeedbackProperties feedback,
                                   String edition) {
        DigestItem item = story.lead();
        Signal signal = story.signal();
        String scoreColor = scoreColor(item.score());
        String safeUrl = safeHref(item.url());
        String rankPrefix = signal != null ? rankEmoji(signal.rank()) : "";
        String radarPrefix = TrendBadgeBuilder.candidateMarker(signal);
        String safeTitle = rankPrefix + radarPrefix + escapeHtml(item.title());
        String typeLabel = item.type() != null ? item.type() : "OTHER";
        String engagementBadge = formatEngagement(item.engagementScore(), item.source());
        String rowOpen = rowStyle.isEmpty() ? "<tr>" : "<tr style=\"" + rowStyle + "\">";

        return rowOpen
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0\">"
                + "<a href=\"" + safeUrl + "\" style=\"color:#1d4ed8;font-weight:600;"
                + "text-decoration:none\">" + safeTitle + "</a>"
                + FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback)
                + "<div style=\"color:#6b7280;font-size:13px;margin-top:4px\">"
                + escapeHtml(item.summary()) + CLOSE_DIV
                + confirmationLine(story.corroborations(), "#9ca3af")
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + "<span style=\"background:#f1f5f9;color:#475569;padding:2px 6px;"
                + "border-radius:4px\">" + escapeHtml(item.category() != null ? item.category() : "Other")
                + "</span></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + typeBadge(typeLabel) + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;color:#6b7280;font-size:12px\">"
                + escapeHtml(item.source())
                + (engagementBadge.isEmpty() ? "" : "<div style=\"color:#9ca3af;"
                        + "font-size:11px;margin-top:2px\">" + engagementBadge + CLOSE_DIV)
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "text-align:center\"><span style=\"color:" + scoreColor
                + ";font-weight:700;font-size:14px\">" + item.score() + "/10</span></td>"
                + "</tr>";
    }

    private static String rankEmoji(SignalRank rank) {
        return switch (rank) {
            case CRITICAL -> "&#128308; ";
            case STRONG   -> "&#129000; ";
            case MODERATE -> "&#128993; ";
            case WEAK     -> "&#9711; ";
        };
    }

    private static String th(String label) {
        return "<th style=\"padding:8px 12px;text-align:left;color:#6b7280;"
                + "font-weight:500;border-bottom:2px solid #e5e7eb\">" + label + "</th>";
    }
}
