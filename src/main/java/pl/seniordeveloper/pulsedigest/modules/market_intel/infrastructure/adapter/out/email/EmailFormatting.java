package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import java.util.Locale;

/**
 * Presentation helpers shared between email sections — HTML escaping, badge color palette,
 * compact number formatting, and source-specific engagement labels.
 *
 * <p>Kept in its own type so that {@code ReportEmailBuilder} stays focused on document
 * structure and so the helpers can be unit-tested in isolation.
 */
final class EmailFormatting {

    private EmailFormatting() {
    }

    static String escapeHtml(String input) {
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
    static String safeHref(String url) {
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
