package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class ReportEmailBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    public String buildSubject(ReportData report) {
        return "📡 Daily Digest " + LocalDate.now().format(DATE_FMT);
    }

    public String buildHtml(ReportData report) {
        String today = LocalDate.now().format(DATE_FMT);
        List<String> insights = report.topInsights() != null ? report.topInsights() : List.of();
        List<ReportData.DigestItem> items = report.items() != null ? report.items() : List.of();

        return "<!DOCTYPE html>"
                + "<html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Daily Digest " + today + "</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "sans-serif;background:#f9fafb;margin:0;padding:20px\">"
                + "<div style=\"max-width:720px;margin:0 auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + buildHeader(today)
                + buildInsightsSection(insights)
                + buildItemsSection(items)
                + buildFooter()
                + "</div></body></html>";
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private String buildHeader(String today) {
        return "<div style=\"background:#1e293b;padding:24px 28px\">"
                + "<h1 style=\"color:#fff;margin:0;font-size:22px\">&#128225; Daily Tech Digest</h1>"
                + "<p style=\"color:#94a3b8;margin:4px 0 0;font-size:14px\">"
                + today + " &middot; powered by GPT-4o</p>"
                + "</div>";
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
        List<ReportData.DigestItem> research = items.stream()
                .filter(i -> "Research".equals(i.category()))
                .toList();
        List<ReportData.DigestItem> releases = items.stream()
                .filter(i -> "Releases".equals(i.category()))
                .toList();
        List<ReportData.DigestItem> community = items.stream()
                .filter(i -> "Community".equals(i.category()))
                .toList();
        List<ReportData.DigestItem> news = items.stream()
                .filter(i -> !"Research".equals(i.category())
                        && !"Releases".equals(i.category())
                        && !"Community".equals(i.category()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(buildSection("&#128196; Research", research));
        sb.append(buildSection("&#128640; Releases", releases));
        sb.append(buildSection("&#128240; News", news));
        sb.append(buildSection("&#127477;&#127473; Community", community));
        return sb.toString();
    }

    private String buildSection(String title, List<ReportData.DigestItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append(title).append(" (").append(items.size()).append(")</h2>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px\">");
        sb.append("<thead><tr style=\"background:#f9fafb\">");
        sb.append(th("Artykuł"));
        sb.append(th("Kategoria"));
        sb.append(th("&#377;ródło"));
        sb.append(th("Score"));
        sb.append("</tr></thead><tbody>");
        for (ReportData.DigestItem item : items) {
            sb.append(buildItemRow(item));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildItemRow(ReportData.DigestItem item) {
        String scoreColor = item.score() >= 7 ? "#16a34a"
                : item.score() >= 4 ? "#ca8a04"
                  : "#dc2626";
        String safeUrl = escapeHtml(item.url());
        String safeTitle = escapeHtml(item.title());
        String safeSummary = escapeHtml(item.summary());
        String safeSource = escapeHtml(item.source());
        String safeCategory = escapeHtml(item.category() != null ? item.category() : "Other");

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
                + "white-space:nowrap;color:#6b7280;font-size:12px\">" + safeSource + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "text-align:center\"><span style=\"color:" + scoreColor
                + ";font-weight:700;font-size:14px\">" + item.score() + "/10</span></td>"
                + "</tr>";
    }

    private String buildFooter() {
        return "<div style=\"padding:16px 28px;background:#f9fafb;text-align:center;"
                + "color:#9ca3af;font-size:12px\">"
                + "Wygenerowano przez GPT-4o &middot; PulseDigest"
                + "</div>";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
