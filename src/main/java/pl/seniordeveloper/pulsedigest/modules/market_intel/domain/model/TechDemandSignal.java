package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;

/**
 * Aggregated job-market demand signal derived from the monthly Hacker News "Who is hiring?" thread:
 * a ranking of how often each tracked technology appears across the hiring posts. Rendered as a
 * standalone "tech-demand pulse" box in the digest, independent of the LLM-scored item table.
 *
 * @param monthLabel    human-readable month of the source thread (e.g. "czerwiec 2026")
 * @param threadUrl     link to the source HN thread
 * @param totalPostings number of hiring posts (top-level comments) the ranking is based on
 * @param entries       technologies ranked by mentions descending; may be empty
 */
public record TechDemandSignal(String monthLabel, String threadUrl, int totalPostings, List<TechDemandEntry> entries) {

    public TechDemandSignal {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
