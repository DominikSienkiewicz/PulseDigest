package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

/**
 * Support that has ended, or is about to, for the reader's own stack (N08).
 *
 * <p>The one kind of item a news feed structurally cannot deliver: nothing happened, a date simply
 * arrived. Rendered deterministically — an end-of-life date has exactly one correct phrasing.
 *
 * <p>Every section carries the date its table was last checked, and says so loudly once that date
 * is old. A hand-kept table rots silently; a reader who can see the table is two years stale knows
 * to distrust the line, while one who cannot has been told a falsehood with full confidence.
 */
final class EolRadarBuilder {

    /** Past this, the table is reported as stale rather than quietly trusted. */
    private static final int STALE_AFTER_DAYS = 180;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private EolRadarBuilder() {
    }

    static String buildEolRadarSection(List<EolNotice> notices, LocalDate today) {
        if (notices == null || notices.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fef2f2;"
                + "border-bottom:1px solid #fecaca;border-left:4px solid #dc2626\">");
        sb.append("<h2 style=\"color:#991b1b;font-size:15px;margin:0 0 4px\">")
                .append("&#8987; Koniec wsparcia &mdash; Twój stack</h2>");
        sb.append(buildProvenanceLine(notices, today));
        sb.append("<ul style=\"margin:0;padding:0;list-style:none\">");
        for (EolNotice notice : notices) {
            sb.append("<li style=\"margin-bottom:8px;font-size:14px\">")
                    .append("<span style=\"font-weight:600;color:#7f1d1d\">")
                    .append(escapeHtml(notice.name())).append("</span>")
                    .append("<div style=\"font-size:12px;margin-top:2px;color:")
                    .append(notice.isPast(today) ? "#b91c1c" : "#b45309").append("\">")
                    .append(describe(notice, today))
                    .append("</div></li>");
        }
        sb.append("</ul></div>");
        return sb.toString();
    }

    /**
     * When the table was last checked — and a warning when that was long ago.
     *
     * <p>Shown even on a fresh table: a reader who only ever sees the date when it is bad has no
     * way to tell a missing warning from a missing check.
     */
    private static String buildProvenanceLine(List<EolNotice> notices, LocalDate today) {
        LocalDate validAsOf = notices.stream()
                .map(EolNotice::validAsOf)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (validAsOf == null) {
            return "<p style=\"color:#b91c1c;font-size:11px;margin:0 0 10px\">"
                    + "Tabela dat bez oznaczenia aktualności &mdash; zweryfikuj przed działaniem.</p>";
        }
        boolean stale = ChronoUnit.DAYS.between(validAsOf, today) > STALE_AFTER_DAYS;
        return "<p style=\"color:" + (stale ? "#b91c1c;font-weight:600" : "#9f1239")
                + ";font-size:11px;margin:0 0 10px\">"
                + "Tabela sprawdzona " + validAsOf.format(DATE)
                + (stale
                        ? " &mdash; ponad " + STALE_AFTER_DAYS + " dni temu, daty mogły się zmienić."
                        : ".")
                + "</p>";
    }

    private static String describe(EolNotice notice, LocalDate today) {
        if (notice.isPast(today)) {
            long months = ChronoUnit.MONTHS.between(notice.endOfLife(), today);
            String since = months >= 1 ? " (" + months + " " + monthWord(months) + " temu)" : "";
            return "wsparcie skończyło się " + notice.endOfLife().format(DATE) + since
                    + " &mdash; brak łatek bezpieczeństwa";
        }
        long months = notice.monthsUntil(today);
        String when = months < 1
                ? "w tym miesiącu"
                : "za " + months + " " + monthWord(months);
        return "wsparcie kończy się " + notice.endOfLife().format(DATE) + " &mdash; " + when;
    }

    /** Polish needs three forms, and "za 2 miesięcy" in a digest about rigour would be its own bug. */
    private static String monthWord(long months) {
        if (months == 1) {
            return "miesiąc";
        }
        long lastTwo = months % 100;
        long last = months % 10;
        boolean few = last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14);
        return few ? "miesiące" : "miesięcy";
    }
}
