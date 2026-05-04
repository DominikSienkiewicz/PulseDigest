package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class ReportEmailBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    private static final int TOP_PICK_THRESHOLD = 8;
    private static final int SIGNAL_THRESHOLD = 5;

    public String buildSubject(ReportData report) {
        return "📡 Daily Digest " + LocalDate.now().format(DATE_FMT);
    }

    public String buildHtml(ReportData report, ResearchResult research) {
        String today = LocalDate.now().format(DATE_FMT);
        String preheader = report.emailPreview() != null && !report.emailPreview().isBlank()
                ? report.emailPreview()
                : "Twój daily digest tech news z ostatnich 24h";
        List<String> insights = report.topInsights() != null ? report.topInsights() : List.of();
        List<ReportData.DigestItem> items = report.items() != null ? report.items() : List.of();
        String editorial = report.editorial();

        return "<!DOCTYPE html>"
                + "<html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Daily Digest " + today + "</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "sans-serif;background:#f9fafb;margin:0;padding:20px\">"
                + buildPreheader(preheader)
                + "<div style=\"max-width:720px;margin:0 auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + buildHeader(today)
                + buildEditorialSection(editorial)
                + buildInsightsSection(insights)
                + buildItemsSection(items)
                + buildFooter(items.size(), research)
                + "</div></body></html>";
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private String buildPreheader(String preview) {
        return "<div style=\"display:none!important;max-height:0;overflow:hidden;"
                + "mso-hide:all;font-size:1px;line-height:1px;opacity:0;color:transparent\">"
                + escapeHtml(preview)
                + "</div>";
    }

    private String buildHeader(String today) {
        return "<div style=\"background:#1e293b;padding:24px 28px\">"
                + "<h1 style=\"color:#fff;margin:0;font-size:22px\">&#128225; Daily Tech Digest</h1>"
                + "<p style=\"color:#94a3b8;margin:4px 0 0;font-size:14px\">"
                + today + " &middot; powered by GPT-4o</p>"
                + "</div>";
    }

    private String buildEditorialSection(String editorial) {
        if (editorial == null || editorial.isBlank()) {
            return "";
        }
        return "<div style=\"padding:22px 28px;background:#fff;"
                + "border-bottom:1px solid #f1f5f9\">"
                + "<p style=\"margin:0;color:#0f172a;font-size:15px;line-height:1.65;"
                + "font-style:italic\">"
                + escapeHtml(editorial)
                + "</p></div>";
    }

    private String buildInsightsSection(List<String> insights) {
        if (insights.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#eff6ff;"
                + "border-bottom:1px solid #dbeafe\">");
        sb.append("<h2 style=\"color:#1e40af;font-size:15px;margin:0 0 10px\">"
                + "&#128273; Top insights dnia</h2>");
        sb.append("<ul style=\"margin:0;padding-left:20px;color:#1e3a8a;"
                + "font-size:14px;line-height:1.7\">");
        for (String insight : insights) {
            sb.append("<li style=\"margin-bottom:6px\">").append(escapeHtml(insight))
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    private String buildItemsSection(List<ReportData.DigestItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        List<ReportData.DigestItem> topPicks = items.stream()
                .filter(i -> i.score() >= TOP_PICK_THRESHOLD)
                .toList();
        List<ReportData.DigestItem> signals = items.stream()
                .filter(i -> i.score() >= SIGNAL_THRESHOLD && i.score() < TOP_PICK_THRESHOLD)
                .toList();
        List<ReportData.DigestItem> longTail = items.stream()
                .filter(i -> i.score() < SIGNAL_THRESHOLD)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(buildTopPicksSection(topPicks));
        sb.append(buildSignalsSection(signals));
        sb.append(buildLongTailSection(longTail));
        return sb.toString();
    }

    private String buildTopPicksSection(List<ReportData.DigestItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append("&#11088; Top picks (").append(items.size()).append(")</h2>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px\">");
        sb.append("<thead><tr style=\"background:#f9fafb\">");
        sb.append(th("Artykuł"));
        sb.append(th("Kategoria"));
        sb.append(th("Typ"));
        sb.append(th("&#377;ródło"));
        sb.append(th("Score"));
        sb.append("</tr></thead><tbody>");
        for (ReportData.DigestItem item : items) {
            sb.append(buildTopPickRow(item));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildSignalsSection(List<ReportData.DigestItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fafafa;"
                + "border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append("&#128268; Signals (").append(items.size()).append(")</h2>");
        sb.append("<ol style=\"margin:0;padding-left:24px;color:#374151;"
                + "font-size:13px;line-height:1.75\">");
        for (ReportData.DigestItem item : items) {
            sb.append(buildSignalRow(item));
        }
        sb.append("</ol></div>");
        return sb.toString();
    }

    private String buildLongTailSection(List<ReportData.DigestItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:16px 28px;border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#6b7280;font-size:13px;margin:0 0 8px;"
                + "font-weight:600\">")
                .append("Long tail (").append(items.size()).append(")</h2>");
        sb.append("<div style=\"color:#9ca3af;font-size:12px;line-height:1.7\">");
        for (int i = 0; i < items.size(); i++) {
            ReportData.DigestItem item = items.get(i);
            sb.append("<a href=\"").append(escapeHtml(item.url())).append("\" "
                    + "style=\"color:#6b7280;text-decoration:none\">")
                    .append(escapeHtml(item.title()))
                    .append("</a>");
            if (i < items.size() - 1) {
                sb.append(" &middot; ");
            }
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    private String buildTopPickRow(ReportData.DigestItem item) {
        String scoreColor = item.score() >= 7 ? "#16a34a"
                : item.score() >= 4 ? "#ca8a04"
                  : "#dc2626";
        String safeUrl = escapeHtml(item.url());
        String safeTitle = escapeHtml(item.title());
        String safeSummary = escapeHtml(item.summary());
        String safeSource = escapeHtml(item.source());
        String safeCategory = escapeHtml(item.category() != null ? item.category() : "Other");
        String typeLabel = item.type() != null ? item.type() : "OTHER";
        String engagementBadge = formatEngagement(item.engagementScore(), item.source());

        return "<tr>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0\">"
                + "<a href=\"" + safeUrl + "\" style=\"color:#1d4ed8;font-weight:600;"
                + "text-decoration:none\">" + safeTitle + "</a>"
                + "<div style=\"color:#6b7280;font-size:13px;margin-top:4px\">"
                + safeSummary + "</div></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + "<span style=\"background:#f1f5f9;color:#475569;padding:2px 6px;"
                + "border-radius:4px\">" + safeCategory + "</span></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + buildTypeBadge(typeLabel) + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;color:#6b7280;font-size:12px\">"
                + safeSource
                + (engagementBadge.isEmpty() ? "" : "<div style=\"color:#9ca3af;"
                        + "font-size:11px;margin-top:2px\">" + engagementBadge + "</div>")
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "text-align:center\"><span style=\"color:" + scoreColor
                + ";font-weight:700;font-size:14px\">" + item.score() + "/10</span></td>"
                + "</tr>";
    }

    private String buildSignalRow(ReportData.DigestItem item) {
        String safeUrl = escapeHtml(item.url());
        String safeTitle = escapeHtml(item.title());
        String safeSource = escapeHtml(item.source());
        String typeLabel = item.type() != null ? item.type() : "OTHER";
        String engagementBadge = formatEngagement(item.engagementScore(), item.source());

        return "<li style=\"margin-bottom:6px\">"
                + "<a href=\"" + safeUrl + "\" style=\"color:#1d4ed8;"
                + "text-decoration:none;font-weight:500\">" + safeTitle + "</a>"
                + " " + buildTypeBadge(typeLabel)
                + " <span style=\"color:#9ca3af;font-size:12px\">&middot; "
                + safeSource
                + (engagementBadge.isEmpty() ? "" : " &middot; " + engagementBadge)
                + " &middot; " + item.score() + "/10</span>"
                + "</li>";
    }

    private String buildFooter(int selectedCount, ResearchResult research) {
        int rawTotal = research != null ? research.rawTotalCount() : 0;
        int sources = research != null ? research.activeSourceCount() : 0;
        return "<div style=\"padding:16px 28px;background:#f9fafb;text-align:center;"
                + "color:#9ca3af;font-size:12px\">"
                + "Wybrano " + selectedCount + " z " + rawTotal + " itemów &middot; "
                + sources + " &#378;róde&#322; &middot; okno 24h"
                + "<br>Wygenerowano przez GPT-4o &middot; PulseDigest"
                + "</div>";
    }

    private String buildTypeBadge(String type) {
        String[] colors = typeBadgeColors(type);
        String bg = colors[0];
        String fg = colors[1];
        String label = escapeHtml(type);
        return "<span style=\"background:" + bg + ";color:" + fg + ";padding:2px 6px;"
                + "border-radius:4px;font-size:11px;font-weight:600\">" + label + "</span>";
    }

    private String[] typeBadgeColors(String type) {
        return switch (type) {
            case "RELEASE"     -> new String[]{"#ede9fe", "#6d28d9"};
            case "FEATURE"     -> new String[]{"#dbeafe", "#1d4ed8"};
            case "LAUNCH"      -> new String[]{"#ffedd5", "#c2410c"};
            case "BREAKTHROUGH" -> new String[]{"#fce7f3", "#9d174d"};
            case "TREND"       -> new String[]{"#ccfbf1", "#0f766e"};
            case "INCIDENT"    -> new String[]{"#fee2e2", "#b91c1c"};
            case "OPINION"     -> new String[]{"#f3f4f6", "#374151"};
            case "DISCUSSION"  -> new String[]{"#fef9c3", "#854d0e"};
            case "RESOURCE"    -> new String[]{"#dcfce7", "#15803d"};
            case "HIRING"      -> new String[]{"#d1fae5", "#065f46"};
            default            -> new String[]{"#f1f5f9", "#475569"};
        };
    }

    private String formatEngagement(Integer score, String source) {
        if (score == null || score <= 0 || source == null) {
            return "";
        }
        String label = engagementLabel(source);
        return formatNumber(score) + " " + label;
    }

    private String engagementLabel(String source) {
        if (source.startsWith("Twitter")) {
            return "&#10084;"; // heart
        }
        if (source.startsWith("Hacker News")) {
            return "pkt";
        }
        if (source.startsWith("GitHub")) {
            return "&#9733;"; // star
        }
        if (source.startsWith("Reddit")) {
            return "&#8593;"; // up arrow
        }
        return "";
    }

    private String formatNumber(int n) {
        if (n >= 1000) {
            double k = n / 1000.0;
            return String.format(Locale.US, k >= 10 ? "%.0fk" : "%.1fk", k);
        }
        return String.valueOf(n);
    }

    private String th(String label) {
        return "<th style=\"padding:8px 12px;text-align:left;color:#6b7280;"
                + "font-weight:500;border-bottom:2px solid #e5e7eb\">" + label + "</th>";
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
