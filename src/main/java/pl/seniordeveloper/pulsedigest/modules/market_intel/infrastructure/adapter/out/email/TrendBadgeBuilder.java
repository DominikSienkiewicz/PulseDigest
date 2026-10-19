package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Calibration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceIntake;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

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
        // The signal is null for an unscored edition, and since 2026-10-18 this badge renders in
        // Must-know, Deals and the tables too — sections whose stories can legitimately have none,
        // unlike the critical block it used to be confined to.
        if (signal == null) {
            return "";
        }
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

    /**
     * The 🟠 prefix on a story the radar expects to break next. Deliberately a title marker, not a
     * new {@code SignalRank}: candidacy is orthogonal to rank (a STRONG signal can be a candidate),
     * and folding it into the rank enum would corrupt the sort that rank exists to drive.
     */
    static String candidateMarker(Signal signal) {
        return signal != null && signal.isCriticalCandidate() ? "&#128992; " : "";
    }

    /**
     * Discloses the living reader model in the footer: what the digest believes about the reader and
     * the evidence behind each claim.
     *
     * <p>Not a nicety. A model distilled from the reader's own clicks, silently reshaping what he
     * sees, is exactly the thing he must be able to audit — and disagree with.
     */
    static String buildReaderModelLine(ReaderProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return "";
        }
        String claims = profile.hypotheses().stream()
                .map(h -> escapeHtml(h.statement()) + " <span style=\"color:#6b7280\">("
                        + escapeHtml(h.evidence()) + ")</span>")
                .collect(Collectors.joining("<br>"));
        return "<div style=\"margin-top:10px;color:#4b5563;font-size:11px;line-height:1.6\">"
                + "Model czytelnika &mdash; destylowany z Twoich głosów:<br>" + claims + "</div>";
    }

    /**
     * The radar's published hit rate. Absent until at least one prediction's verdict window has
     * closed — a feature that predicts must be willing to be measured, but not before it has data.
     */
    static String buildRadarAccuracyLine(RadarAccuracy accuracy) {
        if (accuracy == null || !accuracy.hasVerdict()) {
            return "";
        }
        return " &middot; radar: " + accuracy.confirmed() + "/" + accuracy.flagged()
                + " kandydatów osiągnęło CRITICAL (" + Math.round(accuracy.hitRate() * 100) + "%)";
    }

    /**
     * The 🔴 hit rate the reader's own votes produced (N12): of the stories past editions called
     * CRITICAL, how many he up-voted.
     *
     * <p>Absent until there are at least three to divide by — "100% (1/1)" is a number that looks
     * like calibration and is not. This is the badge's only assessment that comes from outside the
     * system; {@code buildRadarAccuracyLine} grades the radar against the scorer's own later
     * verdict, which is the digest marking its own homework.
     */
    /**
     * Names the sources that have gone quiet, in the footer the reader actually sees (N27).
     *
     * <p>The run log has warned about these since F7, and nobody reads a green run's log. A source
     * that is reachable and returning nothing produces no failure anywhere, so without this line the
     * digest quietly narrows and only the person who goes looking finds out.
     */
    static String buildSilentSourcesLine(java.util.List<SourceIntake> silent) {
        if (silent == null || silent.isEmpty()) {
            return "";
        }
        String names = silent.stream()
                .map(intake -> EmailFormatting.escapeHtml(intake.source()))
                .collect(Collectors.joining(", "));
        return "<div style=\"margin-top:10px;color:#b45309;font-size:11px\">"
                + "&#9888;&#65039; Milcz&#261;ce &#378;r&oacute;d&#322;a: " + names
                + " &mdash; odpowiadaj&#261;, ale nie wnosz&#261; nic od kilku wyda&#324;."
                + " Wy&#322;&#261;cz je w <code>report.sources.disabled</code>, je&#347;li to sta&#322;e.</div>";
    }

    static String buildCalibrationLine(Calibration calibration) {
        if (calibration == null || !calibration.isReportable()) {
            return "";
        }
        return "<br>kalibracja: &#128308; trafia w " + calibration.percent() + "% ("
                + calibration.upvoted() + "/" + calibration.criticalItems()
                + " oznaczonych krytycznie dosta\u0142o &#128077;)";
    }
}
