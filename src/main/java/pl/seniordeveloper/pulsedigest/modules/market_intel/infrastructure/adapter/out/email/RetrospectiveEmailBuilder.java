package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionOutcome;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionScoreboard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceYield;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.safeHref;

/**
 * Renders the quarterly retrospective (N20), including the graded 🔴 badge (N11).
 *
 * <p>Shares the digest's shell so it arrives looking like the same product, and nothing else: no
 * scoring, no feedback links, no sections that would imply this is news. It is a record, and the
 * one section that matters is the one where the system marks its own homework.
 */
@Component
public class RetrospectiveEmailBuilder {

    private static final String BARE_LIST_OPEN = "<ul style=\"margin:0;padding:0;list-style:none\">";
    private static final String ANCHOR_OPEN = "<a href=\"";
    private static final String LIST_CLOSE = "</ul></div>";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    public String buildSubject(Retrospective retrospective) {
        return "📊 PulseDigest — kwartał " + retrospective.from().format(DATE)
                + " – " + retrospective.to().format(DATE);
    }

    public String buildHtml(Retrospective retrospective) {
        return "<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>PulseDigest — retrospektywa</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
                + "background:#f9fafb;margin:0;padding:20px\">"
                + "<div style=\"max-width:720px;margin:0 auto;background:#fff;border-radius:12px;"
                + "overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + header(retrospective)
                + scoreboard(retrospective.scoreboard())
                + endorsed(retrospective)
                + persistent(retrospective)
                + yields(retrospective)
                + footer(retrospective)
                + "</div></body></html>";
    }

    private String header(Retrospective retrospective) {
        return "<div style=\"background:#1e293b;padding:24px 28px\">"
                + "<h1 style=\"color:#fff;margin:0;font-size:22px\">📊 Retrospektywa kwartalna</h1>"
                + "<p style=\"color:#94a3b8;margin:4px 0 0;font-size:14px\">"
                + retrospective.from().format(DATE) + " &ndash; " + retrospective.to().format(DATE)
                + " &middot; " + retrospective.editions() + " wydań</p></div>";
    }

    /**
     * The section the whole edition exists for: what the badge claimed, and what came of it.
     *
     * <p>Rendered even when the rate is bad — especially then. A scoreboard that only appears on a
     * good quarter is an advertisement.
     */
    private String scoreboard(PredictionScoreboard board) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fff1f2;border-bottom:1px solid #fecdd3;"
                + "border-left:4px solid #e11d48\">");
        sb.append("<h2 style=\"color:#9f1239;font-size:15px;margin:0 0 4px\">")
                .append("🔴 Co się stało z krytycznymi trendami</h2>");
        if (!board.isReportable()) {
            sb.append("<p style=\"color:#9f1239;font-size:13px;margin:0\">")
                    .append("Za mało dojrzałych zgłoszeń, by podać wskaźnik ")
                    .append("(").append(board.graded()).append(" z wymaganych 3). ")
                    .append("Liczba bez pokrycia byłaby gorsza niż jej brak.</p></div>");
            return sb.toString();
        }
        sb.append("<p style=\"color:#9f1239;font-size:13px;margin:0 0 10px\">")
                .append("<strong>").append(board.heldUp()).append(" z ").append(board.graded())
                .append("</strong> oznaczeń 🔴 obroniło się — ")
                .append(board.hitRatePercent()).append("%. Oceniane są tylko zgłoszenia starsze ")
                .append("niż ").append(board.maturityDays())
                .append(" dni, bo młodsze nie miały szansy się potwierdzić.</p>");
        sb.append(BARE_LIST_OPEN);
        for (PredictionOutcome outcome : board.outcomes()) {
            sb.append("<li style=\"margin-bottom:8px;font-size:14px\">")
                    .append(ANCHOR_OPEN).append(safeHref(outcome.url()))
                    .append("\" style=\"color:#9f1239;font-weight:600;text-decoration:none\">")
                    .append(escapeHtml(outcome.title())).append("</a>")
                    .append("<div style=\"color:").append(outcome.heldUp() ? "#15803d" : "#6b7280")
                    .append(";font-size:12px;margin-top:2px\">")
                    .append(outcome.heldUp() ? "&#10003; " : "&#8212; ")
                    .append(escapeHtml(outcome.verdict()))
                    .append("</div></li>");
        }
        return sb.append(LIST_CLOSE).toString();
    }

    private String endorsed(Retrospective retrospective) {
        if (retrospective.endorsed().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#f0fdf4;border-bottom:1px solid #bbf7d0;"
                + "border-left:4px solid #16a34a\">");
        sb.append("<h2 style=\"color:#166534;font-size:15px;margin:0 0 10px\">")
                .append("👍 Co doceniłeś</h2>").append(BARE_LIST_OPEN);
        for (DigestItem item : retrospective.endorsed()) {
            sb.append("<li style=\"margin-bottom:6px;font-size:14px\">")
                    .append(ANCHOR_OPEN).append(safeHref(item.url()))
                    .append("\" style=\"color:#166534;text-decoration:none\">")
                    .append(escapeHtml(item.title())).append("</a></li>");
        }
        return sb.append(LIST_CLOSE).toString();
    }

    private String persistent(Retrospective retrospective) {
        if (retrospective.persistent().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#eff6ff;border-bottom:1px solid #bfdbfe;"
                + "border-left:4px solid #2563eb\">");
        sb.append("<h2 style=\"color:#1e40af;font-size:15px;margin:0 0 10px\">")
                .append("📈 Tematy, które nie zniknęły</h2>")
                .append(BARE_LIST_OPEN);
        for (Retrospective.RecurringStory story : retrospective.persistent()) {
            sb.append("<li style=\"margin-bottom:6px;font-size:14px;color:#1e3a8a\">")
                    .append(ANCHOR_OPEN).append(safeHref(story.url()))
                    .append("\" style=\"color:#1e40af;text-decoration:none\">")
                    .append(escapeHtml(story.title())).append("</a>")
                    .append(" <span style=\"color:#4b5563;font-size:12px\">")
                    .append(story.appearances()).append(" wydań</span></li>");
        }
        return sb.append(LIST_CLOSE).toString();
    }

    private String yields(Retrospective retrospective) {
        if (retrospective.yields().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fafafa;border-bottom:1px solid #e5e7eb\">");
        sb.append("<h2 style=\"color:#374151;font-size:15px;margin:0 0 10px\">")
                .append("⚙️ Co wniosły źródła</h2>")
                .append(BARE_LIST_OPEN);
        for (SourceYield sourceYield : retrospective.yields()) {
            sb.append("<li style=\"margin-bottom:4px;font-size:13px;color:#4b5563\">")
                    .append(escapeHtml(sourceYield.source())).append(": ")
                    .append(sourceYield.appearances()).append(" opublikowanych, ")
                    .append(sourceYield.highRankAppearances()).append(" wysokiej rangi (")
                    .append(Math.round(sourceYield.yieldRatio() * 100)).append("%)</li>");
        }
        return sb.append(LIST_CLOSE).toString();
    }

    private String footer(Retrospective retrospective) {
        return "<div style=\"padding:16px 28px;background:#f9fafb;text-align:center;"
                + "color:#6b7280;font-size:12px\">"
                + "Zbudowane wyłącznie z zapisanych wydań — "
                + "żadnego pobrania na żywo, więc ten sam kwartał zawsze da ten sam wynik."
                + "<br>Okno: " + retrospective.scoreboard().windowDays() + " dni &middot; PulseDigest</div>";
    }
}
