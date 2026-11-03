package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * Conference deadlines the reader can still act on (N09).
 *
 * <p>The other half of a conference source. Talks that already happened are something to watch;
 * a call that closes in three weeks is something to do, and it is the half a news feed never
 * carries because nothing happened — a date is simply approaching.
 */
final class CallForPapersBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private CallForPapersBuilder() {
    }

    static String buildCallForPapersSection(List<CallForPapers> calls, LocalDate today) {
        if (calls == null || calls.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fdf4ff;"
                + "border-bottom:1px solid #f5d0fe;border-left:4px solid #a21caf\">");
        sb.append("<h2 style=\"color:#86198f;font-size:15px;margin:0 0 4px\">")
                .append("&#127908; Call for Papers &mdash; jeszcze otwarte</h2>");
        sb.append("<p style=\"color:#a21caf;font-size:11px;margin:0 0 10px\">")
                .append("Terminy zg&#322;osze&#324;, nie relacje z prelekcji.</p>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (CallForPapers call : calls) {
            sb.append("<li style=\"margin-bottom:8px;font-size:14px\">")
                    .append("<a href=\"").append(safeHref(call.url()))
                    .append("\" style=\"color:#86198f;font-weight:600;text-decoration:none\">")
                    .append(escapeHtml(call.conference())).append("</a>")
                    .append("<div style=\"font-size:12px;margin-top:2px;color:")
                    .append(call.isUrgent(today) ? "#be123c" : "#a21caf").append("\">")
                    .append(deadline(call, today))
                    .append(location(call))
                    .append("</div></li>");
        }
        return sb.append("</ul></div>").toString();
    }

    /** The number of days, because "do 14.11.2026" alone makes the reader do the arithmetic. */
    private static String deadline(CallForPapers call, LocalDate today) {
        long left = call.daysLeft(today);
        return "zg&#322;oszenia do " + call.deadline().format(DATE) + " &mdash; " + urgency(left);
    }

    private static String urgency(long daysLeft) {
        if (daysLeft == 0) {
            return "dzi&#347; ostatni dzie&#324;";
        }
        if (daysLeft == 1) {
            return "zosta&#322; 1 dzie&#324;";
        }
        return "zosta&#322;o " + daysLeft + " dni";
    }

    private static String location(CallForPapers call) {
        return call.location() == null || call.location().isBlank()
                ? "" : " &middot; " + escapeHtml(call.location());
    }
}
