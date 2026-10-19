package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;

import java.util.List;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.confirmationLine;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.formatEngagement;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.safeHref;
import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.typeBadge;

/**
 * Renders the two tiered tables — ⭐ Top picks and 🔌 Signals — which share the same columns and differ
 * only in header style and row background.
 *
 * <p>Both list what {@link DigestLayout} did not already place in a block above them, in the order the
 * model emitted it, so the tables read as an index of the remainder rather than a second pass over
 * material the reader has met.
 */
final class DigestTableBuilder {

    private static final String CLOSE_DIV = "</div>";

    private DigestTableBuilder() {
    }

    static String buildItemsSection(List<DigestStory> topPicks, List<DigestStory> signals,
                                    FeedbackProperties feedback, String edition, boolean explainScores) {
        return buildTopPicksSection(topPicks, feedback, edition, explainScores)
                + buildMidTierSection(signals, feedback, edition, explainScores);
    }

    private static String buildTopPicksSection(List<DigestStory> stories, FeedbackProperties feedback,
                                               String edition, boolean explainScores) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px\">");
        sb.append("<h2 style=\"color:#111827;font-size:15px;margin:0 0 12px\">")
                .append("&#11088; Top picks (").append(stories.size()).append(")</h2>");
        sb.append(tableHead("#f9fafb", "14px"));
        for (DigestStory story : stories) {
            sb.append(buildRow(story, "", feedback, edition, explainScores));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // Renders the "Signals" email section; named mid-tier to avoid collision with the Signal domain type.
    private static String buildMidTierSection(List<DigestStory> stories, FeedbackProperties feedback,
                                              String edition, boolean explainScores) {
        if (stories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:20px 28px;background:#fafafa;border-top:1px solid #f0f0f0\">");
        sb.append("<h2 style=\"color:#374151;font-size:15px;margin:0 0 12px\">")
                .append("&#128268; Signals (").append(stories.size()).append(")</h2>");
        sb.append(tableHead("#f3f4f6", "13px"));
        for (DigestStory story : stories) {
            sb.append(buildRow(story, "background:#fafafa", feedback, edition, explainScores));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String tableHead(String headerBg, String fontSize) {
        return "<table style=\"width:100%;border-collapse:collapse;font-size:" + fontSize + "\">"
                + "<thead><tr style=\"background:" + headerBg + "\">"
                + th("Artyku&#322;") + th("Kategoria") + th("Typ") + th("&#377;r&oacute;d&#322;o") + th("Score")
                + "</tr></thead><tbody>";
    }

    /**
     * Darkened in the N16 pass. The score renders at 14px bold, which WCAG does not count as large
     * text (that starts at 18.66px bold), so these need 4.5:1 like everything else — the previous
     * green and amber managed 3.3 and 2.9. The score is also never the only carrier of its own
     * meaning: the number itself is right there in the text.
     */
    private static String scoreColor(int score) {
        if (score >= 7) {
            return "#15803d";
        }
        if (score >= 4) {
            return "#a16207";
        }
        return "#dc2626";
    }

    private static String buildRow(DigestStory story, String rowStyle, FeedbackProperties feedback,
                                   String edition, boolean explainScores) {
        DigestItem item = story.lead();
        Signal signal = story.signal();
        String scoreColor = scoreColor(item.score());
        String safeUrl = safeHref(item.url());
        String rankPrefix = signal != null ? rankEmoji(signal.rank()) : "";
        String radarPrefix = TrendBadgeBuilder.candidateMarker(signal);
        String safeTitle = rankPrefix + radarPrefix + escapeHtml(item.title());
        String typeLabel = item.type() != null ? item.type() : "OTHER";
        String engagementBadge = formatEngagement(item.engagementScore(), item.source());
        String rowOpen = rowStyle.isEmpty() ? "<tr>" : "<tr style=\"" + rowStyle + "\">";

        return rowOpen
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0\">"
                + "<a href=\"" + safeUrl + "\" style=\"color:#1d4ed8;font-weight:600;"
                + "text-decoration:none\">" + safeTitle + "</a>"
                + StackMarkerBuilder.buildStackMarker(story)
                + FeedbackLinkBuilder.render(item.url(), item.source(), item.category(), edition, feedback)
                + "<div style=\"color:#4b5563;font-size:13px;margin-top:4px\">"
                + rankLabel(signal) + escapeHtml(item.summary()) + CLOSE_DIV
                + confirmationLine(story.corroborations(), "#6b7280")
                + (explainScores ? ScoreExplanationBuilder.buildCompactArithmetic(signal) : "")
                + TrendBadgeBuilder.buildRecurrenceBadge(signal)
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + "<span style=\"background:#f1f5f9;color:#475569;padding:2px 6px;"
                + "border-radius:4px\">" + escapeHtml(item.category() != null ? item.category() : "Other")
                + "</span></td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;font-size:12px\">"
                + typeBadge(typeLabel) + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "white-space:nowrap;color:#4b5563;font-size:12px\">"
                + escapeHtml(item.source())
                + (engagementBadge.isEmpty() ? "" : "<div style=\"color:#6b7280;"
                        + "font-size:11px;margin-top:2px\">" + engagementBadge + CLOSE_DIV)
                + "</td>"
                + "<td style=\"padding:10px 12px;border-bottom:1px solid #f0f0f0;"
                + "text-align:center\"><span style=\"color:" + scoreColor
                + ";font-weight:700;font-size:14px\">" + item.score() + "/10</span></td>"
                + "</tr>";
    }

    /**
     * The coloured dot, labelled. 🔴 🟨 🟡 ⚪ differ from one another <em>only</em> by colour, so to a
     * colour-blind reader, or in a client that renders emoji as boxes, the rank simply vanished
     * (N16). The label is what a screen reader announces instead of "red circle", which is a colour
     * and not a meaning; {@link #rankWord} then repeats it as real text, because ARIA support across
     * mail clients is too patchy to be the only carrier of something this load-bearing.
     */
    private static String rankEmoji(SignalRank rank) {
        String glyph = switch (rank) {
            case CRITICAL -> "&#128308;";
            case STRONG   -> "&#129000;";
            case MODERATE -> "&#128993;";
            case WEAK     -> "&#9711;";
        };
        return "<span role=\"img\" aria-label=\"" + rankWord(rank) + "\">" + glyph + "</span> ";
    }

    /**
     * The rank, written out, ahead of the summary. Costs one word per row and is the only form of it
     * that survives a monochrome print, a client that drops emoji and a reader who cannot tell the
     * dots apart.
     */
    private static String rankLabel(Signal signal) {
        if (signal == null || signal.rank() == null) {
            return "";
        }
        return "<strong>" + rankWord(signal.rank()) + "</strong> &middot; ";
    }

    /** The rank as a word, for the metadata line — text that no palette or font can take away. */
    static String rankWord(SignalRank rank) {
        return switch (rank) {
            case CRITICAL -> "Krytyczny";
            case STRONG   -> "Silny";
            case MODERATE -> "Umiarkowany";
            case WEAK     -> "S\u0142aby";
        };
    }

    /**
     * {@code scope="col"} rather than {@code role="presentation"} on the table: this one carries real
     * column headers, so declaring it presentational would strip the association a screen reader uses
     * to say "Score: 9/10" instead of reading a bare number in the fifth cell (N16).
     */
    private static String th(String label) {
        return "<th scope=\"col\" style=\"padding:8px 12px;text-align:left;color:#6b7280;"
                + "font-weight:500;border-bottom:2px solid #e5e7eb\">" + label + "</th>";
    }
}
