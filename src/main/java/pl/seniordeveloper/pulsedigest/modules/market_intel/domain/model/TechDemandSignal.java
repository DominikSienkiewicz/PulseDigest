package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;

/**
 * Aggregated job-market demand signal from the monthly Hacker News "Who is hiring?" thread:
 * which technologies are most in demand (by share of hiring posts), how that shifted versus the
 * previous month, where the reader's own stack lands, and a one-line interpretation. Rendered as a
 * standalone "tech-demand pulse" box, independent of the LLM-scored item table.
 *
 * @param monthLabel         human-readable month of the source thread (e.g. "June 2026")
 * @param previousMonthLabel month used for the month-over-month delta; {@code null} if unavailable
 * @param threadUrl          link to the source HN thread
 * @param totalPostings      number of hiring posts (top-level comments) the ranking is based on
 * @param narrative          one-sentence LLM interpretation; {@code null}/blank until generated
 * @param entries            top technologies ranked by share descending
 * @param stackEntries       the reader's priority technologies (in configured order), for the
 *                           "your stack" line — shown even when outside the top ranking
 */
public record TechDemandSignal(
        String monthLabel,
        String previousMonthLabel,
        String threadUrl,
        int totalPostings,
        String narrative,
        List<TechDemandEntry> entries,
        List<TechDemandEntry> stackEntries) {

    public TechDemandSignal {
        entries = entries != null ? List.copyOf(entries) : List.of();
        stackEntries = stackEntries != null ? List.copyOf(stackEntries) : List.of();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Copy with the LLM interpretation attached (other fields unchanged). */
    public TechDemandSignal withNarrative(String newNarrative) {
        return new TechDemandSignal(
                monthLabel, previousMonthLabel, threadUrl, totalPostings, newNarrative, entries, stackEntries);
    }
}
