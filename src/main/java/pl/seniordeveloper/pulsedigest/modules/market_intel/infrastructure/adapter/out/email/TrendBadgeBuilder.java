package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders the cross-edition memory of a signal: how many consecutive editions have carried the story
 * and when the reader first saw it.
 *
 * <p>Extracted from {@code ReportEmailBuilder} to keep that class under the 500-line budget, the
 * same reason {@code DigestHighlightBuilder} exists.
 */
final class TrendBadgeBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    private TrendBadgeBuilder() {
    }

    /**
     * Empty for a story appearing for the first time — a badge reading "1st edition" would be noise,
     * and the trajectory is the whole point of showing it at all.
     */
    static String buildRecurrenceBadge(Signal signal) {
        TrendRecurrence recurrence = signal.recurrence();
        if (recurrence == null || !recurrence.isBuilding()) {
            return "";
        }
        String firstSeen = recurrence.firstSeenAt() != null
                ? " &middot; Pierwszy sygnał: " + recurrence.firstSeenAt().format(DATE_FMT)
                : "";
        return "<div style=\"color:#9f1239;font-size:11px;font-weight:600;margin-top:2px\">"
                + "&#128200; narasta &mdash; " + recurrence.editionStreak() + ". edycja z rzędu"
                + firstSeen
                + "</div>";
    }
}
