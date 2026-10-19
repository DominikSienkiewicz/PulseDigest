package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;

import java.time.LocalDate;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * What the reader kept and has not come back to (N05 follow-up).
 *
 * <p>The section that makes the third action link worth pressing. Until it existed, "zapisz" wrote a
 * row nothing read, and a button with no visible effect is one a reader stops using.
 *
 * <p>Worded for what the data actually supports. The digest cannot know whether an item was read —
 * nothing observes the reader opening a link — so the heading says <em>saved</em> and the subline
 * says what removes an item from the shelf: a later 👍 or 👎 on the same story. Claiming "unread"
 * would be the kind of confident overstatement that costs a section its credibility the first time
 * a reader sees something listed that they finished a week ago.
 */
final class SavedBacklogBuilder {

    private SavedBacklogBuilder() {
    }

    static String buildSavedBacklogSection(SavedBacklog backlog, LocalDate today) {
        if (backlog == null || backlog.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f8fafc;"
                + "border-bottom:1px solid #e2e8f0;border-left:4px solid #64748b\">");
        sb.append("<h2 style=\"color:#334155;font-size:15px;margin:0 0 4px\">")
                .append("&#128278; Zapisane na p&oacute;&#378;niej</h2>");
        sb.append("<p style=\"color:#64748b;font-size:11px;margin:0 0 10px\">")
                .append("Odk&#322;adasz je przyciskiem &bdquo;zapisz&rdquo;. ")
                .append("Znikaj&#261; st&#261;d, gdy ocenisz je kciukiem &mdash; ")
                .append("czy je przeczyta&#322;e&#347;, tego digest nie wie.</p>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (SavedItem item : backlog.shown()) {
            sb.append("<li style=\"margin-bottom:8px;font-size:14px\">")
                    .append("<a href=\"").append(safeHref(item.url()))
                    .append("\" style=\"color:#334155;font-weight:600;text-decoration:none\">")
                    .append(escapeHtml(item.label())).append("</a>")
                    .append("<div style=\"color:#64748b;font-size:12px;margin-top:2px\">")
                    .append(origin(item)).append(waited(item, today))
                    .append("</div></li>");
        }
        sb.append("</ul>");
        sb.append(remainder(backlog));
        sb.append("</div>");
        return sb.toString();
    }

    private static String origin(SavedItem item) {
        String source = item.source();
        return source == null || source.isBlank() ? "" : escapeHtml(source) + " &middot; ";
    }

    private static String waited(SavedItem item, LocalDate today) {
        long days = item.daysWaiting(today);
        if (days <= 0) {
            return "od&#322;o&#380;one dzi&#347;";
        }
        return "od&#322;o&#380;one " + days + (days == 1 ? " dzie&#324;" : " dni") + " temu";
    }

    /**
     * Names what the cap is hiding. A section that truncates in silence teaches the reader that the
     * number it shows is the number there is, and that lesson is expensive to unlearn.
     */
    private static String remainder(SavedBacklog backlog) {
        int remaining = backlog.remaining();
        if (remaining <= 0) {
            return "";
        }
        return "<p style=\"color:#64748b;font-size:11px;margin:8px 0 0\">"
                + "&hellip; i jeszcze " + remaining + " na p&oacute;&#322;ce.</p>";
    }
}
