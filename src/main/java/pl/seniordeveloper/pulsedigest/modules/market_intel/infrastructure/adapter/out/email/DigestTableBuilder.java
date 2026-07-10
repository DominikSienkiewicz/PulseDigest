package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;

import java.util.List;
import java.util.Map;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.formatEngagement;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

/**
 * Renders the two tiered item tables — ⭐ Top picks (score ≥ 8) and 🔌 Signals (6–7) — which share the
 * same columns and differ only in header style and row background.
 *
 * <p>Extracted from {@code ReportEmailBuilder} to keep that class under the 500-line file budget,
 * the same reason {@code DigestHighlightBuilder} exists. It had run out of headroom: the next
 * section added to the mail would have broken the build.
 */
final class DigestTableBuilder {

    private static final int TOP_PICK_THRESHOLD = 8;
    private static final int SIGNAL_THRESHOLD = 6;
    private static final String CLOSE_DIV = "</div>";
    private static final String CLOSE_SPAN = "</span>";

    private DigestTableBuilder() {
    }

    static String buildItemsSection(List<DigestItem> items, Map<String, Signal> signalByUrl,
                                   FeedbackProperties feedback, String edition) {
        if (items.isEmpty()) {
            return "";
        }
        List<DigestItem> topPicks = items.stream()
                .filter(i -> i.score() >= TOP_PICK_THRESHOLD)
                .toList();
        List<DigestItem> midTier = items.stream()
                .filter(i -> i.score() >= SIGNAL_THRESHOLD && i.score() < TOP_PICK_THRESHOLD)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(buildTopPicksSection(topPicks, signalByUrl, feedback, edition));
        sb.append(buildMidTierSection(midTier, signalByUrl, feedback, edition));
        return sb.toString();
    }

    private static String buildTopPicksSection(List<DigestItem> items, Map<String, Signal> signalByUrl,
                                              FeedbackProperties feedback, String edition) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append("&#11088; Top picks (").append(items.size()).append(")</h2>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px\">");
        sb.append("<thead><tr style=\"background:#f9fafb\">");
        sb.append(th("Artyku&#322;"));
        sb.append(th("Kategoria"));
        sb.append(th("Typ"));
        sb.append(th("&#377;r&oacute;d&#322;o"));
        sb.append(th("Score"));
        sb.append("</tr></thead><tbody>");
        for (DigestItem item : items) {
            sb.append(buildTopPickRow(item, signalByUrl.get(item.url()), feedback, edition));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // Renders the "Signals" email section (LLM score 5–8); named mid-tier to avoid collision with the Signal domain type.
    private static String buildMidTierSection(List<DigestItem> items, Map<String, Signal> signalByUrl,
                                             FeedbackProperties feedback, String edition) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fafafa;border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#374151;font-size:15px;margin:0 0 12px\">")
                .append("&#128268; Signals (").append(items.size()).append(")</h2>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px\">");
        sb.append("<thead><tr style=\"background:#f3f4f6\">");
        sb.append(th("Artyku&#322;"));
        sb.append(th("Kategoria"));
        sb.append(th("Typ"));
        sb.append(th("&#377;r&oacute;d&#322;o"));
        sb.append(th("Score"));
        sb.append("</tr></thead><tbody>");
        for (DigestItem item : items) {
            sb.append(buildTieredRow(item, "#fafafa", signalByUrl.get(item.url()), feedback, edition));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String buildTopPickRow(DigestItem item, Signal signal,
                                          FeedbackProperties feedback, String edition) {
        return buildRow(item, "", signal, feedback, edition);
    }

    private static String buildTieredRow(DigestItem item, String rowBg, Signal signal,
                                         FeedbackProperties feedback, String edition) {
        return buildRow(item, "background:" + rowBg, signal, feedback, edition);
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

    private static String buildRow(DigestItem item, String rowStyle, Signal signal,
                                   FeedbackProperties feedback, String edition) {
        String scoreColor = scoreColor(item.score());
        String safeUrl = safeHref(item.url());
        String rankPrefix = signal != null ? rankEmoji(signal.rank()) : "";
        String radarPrefix = TrendBadgeBuilder.candidateMarker(signal);
        String safeTitle = rankPrefix + radarPrefix + escapeHtml(item.title());
        String safeSummary = escapeHtml(item.summary());
        String safeSource = escapeHtml(item.source());
        String safeCategory = escapeHtml(item.category() != null ? item.category() : "Other");
        String typeLabel = item.type() != null ? item.type() : "OTHER";
        String engagementBadge = formatEngagement(item.engagementScore(), item.source());
        String rowOpen = rowStyle.isEmpty() ? "<tr>" : "<tr style=\"" + rowStyle + "\">";

        return rowOpen
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0\">"
                + "<a href=\"" + safeUrl + "\" style=\"color:#1d4ed8;font-weight:600;"
                + "text-decoration:none\">" + safeTitle + "</a>"
                + FeedbackLinkBuilder.render(item.url(), item.source(), edition, feedback)
                + "<div style=\"color:#6b7280;font-size:13px;margin-top:4px\">"
                + safeSummary + "</div></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + "<span style=\"background:#f1f5f9;color:#475569;padding:2px 6px;"
                + "border-radius:4px\">" + safeCategory + "</span></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + typeBadge(typeLabel) + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;color:#6b7280;font-size:12px\">"
                + safeSource
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
