package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReadingBudget;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Estimates what an edition costs to read (N04).
 *
 * <p>Two inputs, both already in hand: what kind of thing the item is, and how much the model found
 * to say about it. A release note is a thirty-second check; a paper is a sitting. Those are
 * different enough that a per-type base is more informative than any word count of the summary
 * alone, and the summary length then nudges within the type.
 *
 * <p>The numbers below are judgements, not measurements, which is why nothing here reports a figure
 * per item — the rendering shows a coarse class and one rounded total. A per-item "3 min" would be
 * a precision this estimate does not have, and the reader would believe it.
 */
@Service
public class ReadingTimeService {

    /** Minutes a type typically costs, before the summary nudges it. */
    private static final Map<String, Integer> BY_TYPE = Map.of(
            "release", 2,
            "promotion", 1,
            "launch", 3,
            "feature", 4,
            "news", 4,
            "research", 12,
            "analysis", 8,
            "tutorial", 10);

    private static final int DEFAULT_MINUTES = 5;

    /** Above this many characters of summary, the story is doing more than announcing something. */
    private static final int LONG_SUMMARY_CHARS = 180;

    public ReadingBudget estimate(List<DigestItem> items) {
        if (items == null || items.isEmpty()) {
            return ReadingBudget.NOTHING;
        }
        int total = items.stream().mapToInt(ReadingTimeService::minutesFor).sum();
        return new ReadingBudget(items.size(), roundToFive(total));
    }

    /** A coarse class for one item: enough to triage, too coarse to be mistaken for a measurement. */
    public boolean isQuick(DigestItem item) {
        return minutesFor(item) <= 3;
    }

    private static int minutesFor(DigestItem item) {
        String type = item.type() == null ? "" : item.type().toLowerCase(Locale.ROOT);
        int base = BY_TYPE.getOrDefault(type, DEFAULT_MINUTES);
        String summary = item.summary() == null ? "" : item.summary();
        return summary.length() > LONG_SUMMARY_CHARS ? base + 2 : base;
    }

    /**
     * Rounded to the nearest five minutes, and never to zero. A total of "37 minutes" claims the
     * estimate can tell 37 from 38; "about 35" says what it actually knows.
     */
    private static int roundToFive(int minutes) {
        return Math.max(5, Math.round(minutes / 5.0f) * 5);
    }
}
