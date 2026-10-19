package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .EmailFormatting.escapeHtml;

/**
 * Opens the scoring black box: a micro-line under an item explaining why it surfaced, and a footer
 * block showing what the reader's own votes have done to the weights.
 *
 * <p>The loop this closes is investment → reward. A 👎 whose effect is invisible is a 👎 nobody casts
 * twice, and a learning loop nobody feeds stops learning.
 *
 * <p><b>The arithmetic closes (N29).</b> Every component ends in a printed total, so the reader can
 * check the sum rather than take the badge on faith — which is the difference between a score that
 * is legible and one that merely looks precise. The full line goes under Must-know and Critical
 * trends; the tables get {@link #buildCompactArithmetic(Signal)}, the same numbers without the vote
 * commentary, because twenty copies of the explanation would be noise while twenty copies of the sum
 * are a column. Email clients cannot be relied on for {@code <details>}, so "collapsed" is not an
 * option available here — density is managed by saying less, not by hiding it.
 */
final class ScoreExplanationBuilder {

    /** Separates a starting value from the value it moved to, in every "from → to" pair the mail renders. */
    private static final String ARROW = " &rarr; ";

    private ScoreExplanationBuilder() {
    }

    /** Empty for a signal scored before breakdowns existed — the mail must not invent an explanation. */
    static String buildWhyYouSeeThis(Signal signal) {
        if (signal == null || signal.breakdown() == null) {
            return "";
        }
        ScoreBreakdown b = signal.breakdown();
        StringBuilder sb = new StringBuilder("<div style=\"color:#64748b;font-size:11px;margin-top:3px\">")
                .append("Dlaczego to widzisz: ")
                .append(escapeHtml(b.sourceKey())).append(' ').append(weight(b.effectiveWeight()))
                .append(ARROW).append(b.baseScore()).append(" pkt");
        if (b.engagementBonus() > 0) {
            sb.append(" &middot; engagement +").append(b.engagementBonus());
        }
        if (b.wasCrossSourceConfirmed()) {
            sb.append(" &middot; potwierdzenie w 3+ domenach +").append(b.crossSourceBonus());
        }
        if (b.categoryWasNudged()) {
            sb.append(" &middot; preferencja &times;")
                    .append(String.format(Locale.ROOT, "%.2f", b.categoryMultiplier()));
        }
        if (b.wasMuted()) {
            sb.append(" &middot; wyciszone &times;")
                    .append(String.format(Locale.ROOT, "%.2f", b.muteMultiplier()));
        }
        // The total is the point: a reader who can add up the parts can disagree with the result
        // specifically instead of vaguely.
        sb.append(" = <strong>").append(signal.signalScore()).append(" pkt</strong>");
        if (b.readerInfluenced()) {
            sb.append(" &middot; <span style=\"color:#64748b\">Twoje głosy: ")
                    .append(votesEffect(b)).append("</span>");
        }
        return sb.append("</div>").toString();
    }

    /**
     * The same arithmetic for a table row: components and the total, no vote commentary.
     *
     * <p>Empty for an unscored signal rather than a row of zeros — a table that prints "0 pkt" for
     * everything an older edition carried would be worse than a table that prints nothing.
     */
    static String buildCompactArithmetic(Signal signal) {
        if (signal == null || signal.breakdown() == null) {
            return "";
        }
        ScoreBreakdown b = signal.breakdown();
        StringBuilder sb = new StringBuilder(
                "<div style=\"color:#6b7280;font-size:10px;margin-top:3px\">")
                .append(escapeHtml(b.sourceKey())).append(' ').append(weight(b.effectiveWeight()))
                .append(ARROW).append(b.baseScore());
        if (b.engagementBonus() > 0) {
            sb.append(" +").append(b.engagementBonus());
        }
        if (b.categoryWasNudged()) {
            sb.append(" &times;").append(String.format(Locale.ROOT, "%.2f", b.categoryMultiplier()));
        }
        if (b.wasMuted()) {
            sb.append(" &times;").append(String.format(Locale.ROOT, "%.2f", b.muteMultiplier()));
        }
        if (b.wasCrossSourceConfirmed()) {
            sb.append(" +").append(b.crossSourceBonus());
        }
        return sb.append(" = ").append(signal.signalScore()).append(" pkt</div>").toString();
    }

    private static String votesEffect(ScoreBreakdown b) {
        StringBuilder sb = new StringBuilder();
        if (b.sourceWeightWasNudged()) {
            sb.append("źródło ").append(signed(b.netSourceVotes())).append(" (")
                    .append(weight(b.baseWeight())).append(ARROW).append(weight(b.effectiveWeight()))
                    .append(')');
        }
        if (b.categoryWasNudged()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("kategoria ").append(signed(b.netCategoryVotes()))
                    .append(" (&times;").append(String.format(Locale.ROOT, "%.2f", b.categoryMultiplier()))
                    .append(')');
        }
        return sb.toString();
    }

    /**
     * The footer block: every source whose credibility weight the reader's votes actually moved,
     * reported once, with the weight it started from and the one it ended at.
     */
    static String buildVotesInAction(List<Signal> signals) {
        Map<String, ScoreBreakdown> movedSources = new LinkedHashMap<>();
        signals.stream()
                .map(Signal::breakdown)
                .filter(b -> b != null && b.sourceWeightWasNudged())
                .forEach(b -> movedSources.putIfAbsent(b.sourceKey(), b));
        if (movedSources.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(
                "<div style=\"margin-top:10px;color:#6b7280;font-size:11px;line-height:1.6\">")
                .append("Twoje głosy w akcji:<br>");
        movedSources.values().forEach(b -> sb.append(escapeHtml(b.sourceKey())).append(": ")
                .append(signed(b.netSourceVotes())).append(" głosów &middot; waga ")
                .append(weight(b.baseWeight())).append(ARROW).append(weight(b.effectiveWeight()))
                .append("<br>"));
        return sb.append("</div>").toString();
    }

    private static String weight(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String signed(int votes) {
        return (votes > 0 ? "+" : "") + votes;
    }
}
