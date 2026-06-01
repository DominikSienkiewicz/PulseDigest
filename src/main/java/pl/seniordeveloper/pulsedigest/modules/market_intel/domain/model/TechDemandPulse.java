package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure assembler that turns raw mention counts into a ranked {@link TechDemandSignal}: computes each
 * technology's share of hiring posts, the month-over-month change in that share (in percentage
 * points), the top-N ranking, and the reader's own "stack" line. Narrative is added later.
 */
public final class TechDemandPulse {

    private TechDemandPulse() {
    }

    /**
     * @param monthLabel      current thread month label
     * @param prevMonthLabel  previous thread month label, or {@code null} if no previous thread
     * @param threadUrl       link to the current thread
     * @param current         current month's token → post-count
     * @param currentTotal    current month's number of hiring posts
     * @param previous        previous month's token → post-count ({@code null}/empty disables deltas)
     * @param previousTotal   previous month's number of hiring posts
     * @param priority        the reader's stack tokens, in display order, for the "your stack" line
     * @param minMentions     drop ranking technologies mentioned in fewer than this many posts
     * @param maxTech         keep at most this many technologies in the main ranking
     */
    public static TechDemandSignal assemble(
            String monthLabel, String prevMonthLabel, String threadUrl,
            Map<String, Integer> current, int currentTotal,
            Map<String, Integer> previous, int previousTotal,
            List<String> priority, int minMentions, int maxTech) {

        boolean hasPrevious = previous != null && !previous.isEmpty() && previousTotal > 0;

        List<TechDemandEntry> ranking = new ArrayList<>();
        for (Map.Entry<String, Integer> e : current.entrySet()) {
            if (e.getValue() >= minMentions) {
                ranking.add(entry(e.getKey(), e.getValue(), currentTotal,
                        hasPrevious ? previous : null, previousTotal));
            }
        }
        ranking.sort(Comparator
                .comparingDouble(TechDemandEntry::share).reversed()
                .thenComparing(TechDemandEntry::name));
        List<TechDemandEntry> entries = ranking.size() > maxTech ? ranking.subList(0, maxTech) : ranking;

        List<TechDemandEntry> stackEntries = new ArrayList<>();
        if (priority != null) {
            for (String token : priority) {
                int count = current.getOrDefault(token, 0);
                stackEntries.add(entry(token, count, currentTotal,
                        hasPrevious ? previous : null, previousTotal));
            }
        }

        return new TechDemandSignal(
                monthLabel, hasPrevious ? prevMonthLabel : null, threadUrl, currentTotal,
                null, List.copyOf(entries), List.copyOf(stackEntries));
    }

    private static TechDemandEntry entry(
            String token, int count, int currentTotal, Map<String, Integer> previous, int previousTotal) {
        double share = currentTotal > 0 ? (double) count / currentTotal : 0.0;
        Double deltaPp = null;
        if (previous != null) {
            double prevShare = (double) previous.getOrDefault(token, 0) / previousTotal;
            deltaPp = (share - prevShare) * 100.0;
        }
        return new TechDemandEntry(token, count, share, deltaPp);
    }
}
