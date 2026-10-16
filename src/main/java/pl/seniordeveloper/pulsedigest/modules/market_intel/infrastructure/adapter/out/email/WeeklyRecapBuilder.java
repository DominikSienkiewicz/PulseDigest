package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapChange;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RecapEntry;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.WeeklyRecap;

import java.util.List;
import java.util.Set;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * Renders the Thursday "week in signals" block. Absent from every other edition, and absent on
 * Thursday too when nothing moved — a recap that always appears stops being a payoff.
 *
 * <p>{@code WeeklyRecapService} assembles the recap from <em>this</em> edition's signals, so left
 * whole every line restates a story printed above it. A line therefore earns its place only when it
 * says something those sections cannot:
 *
 * <ul>
 *   <li>it <strong>faded</strong> — a story that was 🔴 and came to nothing has no entry above to
 *       read, and saying so is exactly what makes the 🔴 credible; or</li>
 *   <li>its story never reached the mail, because it scored below the rendering bar.</li>
 * </ul>
 *
 * <p>Extracted from {@code ReportEmailBuilder} to keep that class under the 500-line budget.
 */
final class WeeklyRecapBuilder {

    private WeeklyRecapBuilder() {
    }

    static String buildWeeklyRecapSection(WeeklyRecap recap, Set<String> renderedUrls) {
        if (recap == null || recap.entries().isEmpty()) {
            return "";
        }
        List<RecapEntry> entries = recap.entries().stream()
                .filter(entry -> entry.change() == RecapChange.FADED || !renderedUrls.contains(entry.url()))
                .toList();
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f0f9ff;border-bottom:1px solid #bae6fd\">");
        sb.append("<h2 style=\"color:#075985;font-size:15px;margin:0 0 10px\">")
                .append("&#128202; Tydzień w sygnałach</h2>");
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (RecapEntry entry : entries) {
            sb.append("<li style=\"margin-bottom:8px;font-size:13px\">")
                    .append(marker(entry))
                    .append(" <a href=\"").append(safeHref(entry.url()))
                    .append("\" style=\"color:#0369a1;font-weight:600;text-decoration:none\">")
                    .append(escapeHtml(entry.title())).append("</a>")
                    .append("<span style=\"color:#0c4a6e;font-size:11px\"> &middot; ")
                    .append(entry.previousRank().name()).append(" &rarr; ").append(entry.currentRank().name())
                    .append("</span></li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    private static String marker(RecapEntry entry) {
        return switch (entry.change()) {
            case ESCALATED -> "<span style=\"color:#b91c1c;font-weight:700\">&#9650; urosło</span>";
            case CONFIRMED -> "<span style=\"color:#15803d;font-weight:700\">&#10003; potwierdzony</span>";
            case FADED -> "<span style=\"color:#6b7280;font-weight:700\">&#9660; wygasł</span>";
        };
    }
}
