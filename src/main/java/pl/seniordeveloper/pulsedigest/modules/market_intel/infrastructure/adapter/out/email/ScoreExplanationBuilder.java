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
 * twice, and a learning loop nobody feeds stops learning. The micro-line is rendered only on the
 * blocks the reader actually reads closely — Must-know and Critical trends — because the same line
 * under twenty rows would be noise, not transparency.
 */
final class ScoreExplanationBuilder {

    private ScoreExplanationBuilder() {
    }

    /** Empty for a signal scored before breakdowns existed — the mail must not invent an explanation. */
    static String buildWhyYouSeeThis(Signal signal) {
        if (signal == null || signal.breakdown() == null) {
            return "";
        }
        ScoreBreakdown b = signal.breakdown();
        StringBuilder sb = new StringBuilder("<div style=\"color:#94a3b8;font-size:11px;margin-top:3px\">")
                .append("Dlaczego to widzisz: ")
                .append(escapeHtml(b.sourceKey())).append(' ').append(weight(b.effectiveWeight()))
                .append(" &rarr; ").append(b.baseScore()).append(" pkt");
        if (b.engagementBonus() > 0) {
            sb.append(" &middot; engagement +").append(b.engagementBonus());
        }
        if (b.wasCrossSourceConfirmed()) {
            sb.append(" &middot; potwierdzenie w 3+ domenach +").append(b.crossSourceBonus());
        }
        if (b.readerInfluenced()) {
            sb.append(" &middot; <span style=\"color:#64748b\">Twoje głosy: ")
                    .append(votesEffect(b)).append("</span>");
        }
        return sb.append("</div>").toString();
    }

    private static String votesEffect(ScoreBreakdown b) {
        StringBuilder sb = new StringBuilder();
        if (b.sourceWeightWasNudged()) {
            sb.append("źródło ").append(signed(b.netSourceVotes())).append(" (")
                    .append(weight(b.baseWeight())).append(" &rarr; ").append(weight(b.effectiveWeight()))
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
                "<div style=\"margin-top:10px;color:#9ca3af;font-size:11px;line-height:1.6\">")
                .append("Twoje głosy w akcji:<br>");
        movedSources.values().forEach(b -> sb.append(escapeHtml(b.sourceKey())).append(": ")
                .append(signed(b.netSourceVotes())).append(" głosów &middot; waga ")
                .append(weight(b.baseWeight())).append(" &rarr; ").append(weight(b.effectiveWeight()))
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
