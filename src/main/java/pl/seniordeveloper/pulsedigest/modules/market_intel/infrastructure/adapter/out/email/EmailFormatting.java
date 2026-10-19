package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Presentation helpers shared between email sections — HTML escaping, badge color palette,
 * compact number formatting, and source-specific engagement labels.
 *
 * <p>Kept in its own type so that {@code ReportEmailBuilder} stays focused on document
 * structure and so the helpers can be unit-tested in isolation.
 */
// Public since N31: the archive renders the same data to HTML and needs the same escaping.
// Widened to these two helpers only — everything else here stays an email-package detail, and a
// second implementation of escaping is precisely the duplication that goes wrong quietly.
public final class EmailFormatting {

    private EmailFormatting() {
    }

    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Returns an HTML-attribute-safe href for a (potentially attacker-influenced) URL. Only absolute
     * {@code http}/{@code https} URLs are allowed through; anything else — {@code javascript:},
     * {@code data:}, {@code mailto:}, relative or blank — collapses to {@code "#"} so a crafted scraped
     * URL cannot become a clickable script/redirect in the delivered email. The allowed URL is still
     * HTML-escaped to neutralise attribute breakout.
     */
    public static String safeHref(String url) {
        if (url == null || url.isBlank()) {
            return "#";
        }
        String trimmed = url.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return escapeHtml(trimmed);
        }
        return "#";
    }

    /**
     * Links every source that independently carried one story, or nothing when only one did.
     *
     * <p>This line is what makes collapsing a story into a single entry lossless. The reader meets the
     * story once and can still reach each source's own coverage, so corroboration is visible without
     * the same news being printed three times in three sections. Each section passes its own colour so
     * the line reads as part of the block it sits in.
     */
    static String confirmationLine(List<DigestItem> corroborations, String color) {
        if (corroborations == null || corroborations.size() < 2) {
            return "";
        }
        String links = corroborations.stream()
                .map(item -> "<a href=\"" + safeHref(item.url()) + "\" style=\"color:" + color
                        + ";text-decoration:underline\">" + escapeHtml(item.source()) + "</a>")
                .collect(Collectors.joining(" + "));
        return "<div style=\"color:" + color + ";font-size:11px;margin-top:2px\">Potwierdzone w: "
                + links + "</div>";
    }

    static String[] typeBadgeColors(String type) {
        return switch (type) {
            case "RELEASE"      -> new String[]{"#ede9fe", "#6d28d9"};
            case "FEATURE"      -> new String[]{"#dbeafe", "#1d4ed8"};
            case "LAUNCH"       -> new String[]{"#ffedd5", "#c2410c"};
            case "PROMOTION"    -> new String[]{"#fae8ff", "#a21caf"};
            case "BREAKTHROUGH" -> new String[]{"#fce7f3", "#9d174d"};
            case "TREND"        -> new String[]{"#ccfbf1", "#0f766e"};
            case "INCIDENT"     -> new String[]{"#fee2e2", "#b91c1c"};
            case "OPINION"      -> new String[]{"#f3f4f6", "#374151"};
            case "DISCUSSION"   -> new String[]{"#fef9c3", "#854d0e"};
            case "RESOURCE"     -> new String[]{"#dcfce7", "#15803d"};
            case "HIRING"       -> new String[]{"#d1fae5", "#065f46"};
            default             -> new String[]{"#f1f5f9", "#475569"};
        };
    }

    static String typeBadge(String type) {
        String[] colors = typeBadgeColors(type);
        return "<span style=\"background:" + colors[0] + ";color:" + colors[1] + ";padding:2px 6px;"
                + "border-radius:4px;font-size:11px;font-weight:600\">" + escapeHtml(type) + "</span>";
    }

    static String formatEngagement(Integer score, String source) {
        if (score == null || score <= 0 || source == null) {
            return "";
        }
        return formatNumber(score) + " " + engagementLabel(source);
    }

    static String engagementLabel(String source) {
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

    static String formatNumber(int n) {
        if (n >= 1000) {
            double k = n / 1000.0;
            return String.format(Locale.US, k >= 10 ? "%.0fk" : "%.1fk", k);
        }
        return String.valueOf(n);
    }
}
