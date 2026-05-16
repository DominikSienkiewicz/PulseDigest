package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendInsight;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.formatEngagement;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

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
        List<DigestItem> items = report.items() != null ? report.items() : List.of();
        List<TrendInsight> trends = report.trends() != null ? report.trends() : List.of();
        List<Signal> signals = report.signals() != null ? report.signals() : List.of();
        String editorial = report.editorial();

        List<Signal> criticals = signals.stream().filter(Signal::isCriticalTrend).toList();
        Map<String, SignalRank> rankByUrl = signals.stream()
                .collect(Collectors.toMap(s -> s.item().url(), Signal::rank, (a, b) -> a));

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
                + buildCriticalTrendsSection(criticals)
                + buildTrendsSection(trends)
                + buildItemsSection(items, rankByUrl)
                + buildFooter(items.size(), research)
                + "</div></body></html>";
    }

    // Sections

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

    private String buildTrendsSection(List<TrendInsight> trends) {
        if (trends == null || trends.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f5f3ff;"
                + "border-bottom:1px solid #ede9fe\">");
        sb.append("<h2 style=\"color:#5b21b6;font-size:15px;margin:0 0 10px\">")
                .append("&#128260; W tym tygodniu wraca</h2>");
        sb.append("<ul style=\"margin:0;padding-left:20px;color:#4c1d95;"
                + "font-size:14px;line-height:1.7;list-style:none\">");
        for (TrendInsight t : trends) {
            String narrative = t.narrative() != null && !t.narrative().isBlank()
                    ? " &mdash; " + escapeHtml(t.narrative()) : "";
            sb.append("<li style=\"margin-bottom:8px\">")
                    .append("<strong>").append(escapeHtml(t.category())).append("</strong>")
                    .append(" <span style=\"color:#7c3aed;font-weight:600\">&times;")
                    .append(t.occurrences()).append("</span>")
                    .append(narrative);
            if (t.exampleTitles() != null && !t.exampleTitles().isEmpty()) {
                sb.append("<div style=\"color:#6b21a8;font-size:12px;margin-top:2px;"
                        + "opacity:.85\">np. ");
                for (int i = 0; i < t.exampleTitles().size() && i < 2; i++) {
                    if (i > 0) {
                        sb.append(" &middot; ");
                    }
                    sb.append(escapeHtml(t.exampleTitles().get(i)));
                }
                sb.append("</div>");
            }
            sb.append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    private String buildCriticalTrendsSection(List<Signal> criticals) {
        if (criticals.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fff1f2;"
                + "border-bottom:1px solid #fecdd3;border-left:4px solid #dc2626\">");
        sb.append("<h2 style=\"color:#b91c1c;font-size:15px;margin:0 0 10px\">")
                .append("&#128308; Krytyczne trendy &mdash; potwierdzenie w 3+ typach &#378;r&oacute;de&#322;</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (Signal signal : criticals) {
            DigestItem it = signal.item();
            String domainLabel = signal.sourceDomains().isEmpty() ? ""
                    : " <span style=\"color:#9f1239;font-size:11px;font-weight:600\">"
                    + "[" + signal.sourceDomains().stream()
                            .map(d -> escapeHtml(d.name()))
                            .collect(Collectors.joining(" &middot; "))
                    + "]</span>";
            String summaryPart = (it.summary() != null && !it.summary().isBlank())
                    ? "<div style=\"color:#991b1b;font-size:12px;margin-top:3px\">"
                    + escapeHtml(it.summary()) + " &mdash; " + escapeHtml(it.source()) + "</div>"
                    : "<div style=\"color:#991b1b;font-size:12px;margin-top:3px\">"
                    + escapeHtml(it.source()) + "</div>";
            sb.append("<li style=\"margin-bottom:10px\">")
                    .append("<a href=\"").append(escapeHtml(it.url()))
                    .append("\" style=\"color:#b91c1c;font-weight:600;text-decoration:none;font-size:14px\">")
                    .append(escapeHtml(it.title())).append("</a>")
                    .append(domainLabel)
                    .append(summaryPart)
                    .append("</li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    private String buildItemsSection(List<DigestItem> items, Map<String, SignalRank> rankByUrl) {
        if (items.isEmpty()) {
            return "";
        }
        List<DigestItem> topPicks = items.stream()
                .filter(i -> i.score() >= TOP_PICK_THRESHOLD)
                .toList();
        List<DigestItem> midTier = items.stream()
                .filter(i -> i.score() >= SIGNAL_THRESHOLD && i.score() < TOP_PICK_THRESHOLD)
                .toList();
        List<DigestItem> longTail = items.stream()
                .filter(i -> i.score() < SIGNAL_THRESHOLD)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(buildTopPicksSection(topPicks, rankByUrl));
        sb.append(buildMidTierSection(midTier, rankByUrl));
        sb.append(buildLongTailSection(longTail, rankByUrl));
        return sb.toString();
    }

    private String buildTopPicksSection(List<DigestItem> items, Map<String, SignalRank> rankByUrl) {
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
            sb.append(buildTopPickRow(item, rankByUrl.get(item.url())));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // Renders the "Signals" email section (LLM score 5–8); named mid-tier to avoid collision with the Signal domain type.
    private String buildMidTierSection(List<DigestItem> items, Map<String, SignalRank> rankByUrl) {
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
            sb.append(buildTieredRow(item, "#fafafa", rankByUrl.get(item.url())));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildLongTailSection(List<DigestItem> items, Map<String, SignalRank> rankByUrl) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f9fafb;border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#9ca3af;font-size:14px;margin:0 0 12px\">")
                .append("Long tail (").append(items.size()).append(")</h2>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:12px\">");
        sb.append("<thead><tr style=\"background:#f3f4f6\">");
        sb.append(th("Artyku&#322;"));
        sb.append(th("Kategoria"));
        sb.append(th("Typ"));
        sb.append(th("&#377;r&oacute;d&#322;o"));
        sb.append(th("Score"));
        sb.append("</tr></thead><tbody>");
        for (DigestItem item : items) {
            sb.append(buildTieredRow(item, "#f9fafb", rankByUrl.get(item.url())));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildTopPickRow(DigestItem item, SignalRank rank) {
        String scoreColor = item.score() >= 7 ? "#16a34a"
                : item.score() >= 4 ? "#ca8a04"
                  : "#dc2626";
        return buildRow(item, "", rank);
    }

    private String buildTieredRow(DigestItem item, String rowBg, SignalRank rank) {
        return buildRow(item, "background:" + rowBg, rank);
    }

    private String buildRow(DigestItem item, String rowStyle, SignalRank rank) {
        String scoreColor = item.score() >= 7 ? "#16a34a"
                : item.score() >= 4 ? "#ca8a04"
                  : "#dc2626";
        String safeUrl = escapeHtml(item.url());
        String rankPrefix = rank != null ? rankEmoji(rank) : "";
        String safeTitle = rankPrefix + escapeHtml(item.title());
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
                        + "font-size:11px;margin-top:2px\">" + engagementBadge + "</div>")
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "text-align:center\"><span style=\"color:" + scoreColor
                + ";font-weight:700;font-size:14px\">" + item.score() + "/10</span></td>"
                + "</tr>";
    }

    private String buildFooter(int selectedCount, ResearchResult research) {
        int rawTotal = research != null ? research.rawTotalCount() : 0;
        int sources = research != null ? research.activeSourceCount() : 0;
        long failedSources = research != null
                ? research.sourceFetchReports().stream().filter(r -> r.status() == SourceFetchStatus.FAILED).count()
                : 0;
        String sourceHealth = failedSources > 0 ? " &middot; " + failedSources + " źródła z ostrzeżeniem" : "";
        return "<div style=\"padding:16px 28px;background:#f9fafb;text-align:center;"
                + "color:#9ca3af;font-size:12px\">"
                + "Wybrano " + selectedCount + " z " + rawTotal + " itemów &middot; "
                + sources + " &#378;róde&#322;" + sourceHealth + " &middot; okno 24h"
                + "<br>Wygenerowano przez GPT-4o &middot; PulseDigest"
                + "</div>";
    }

    private static String rankEmoji(SignalRank rank) {
        return switch (rank) {
            case CRITICAL -> "&#128308; ";
            case STRONG   -> "&#129000; ";
            case MODERATE -> "&#128993; ";
            case WEAK     -> "&#9711; ";
        };
    }

    private String th(String label) {
        return "<th style=\"padding:8px 12px;text-align:left;color:#6b7280;"
                + "font-weight:500;border-bottom:2px solid #e5e7eb\">" + label + "</th>";
    }
}
