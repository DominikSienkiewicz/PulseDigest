package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Roughly how long this edition asks for (N04).
 *
 * <p>Turns the digest from a list into a decision: the reader can tell before scrolling whether this
 * is a coffee or an evening.
 *
 * <p>Estimated from the item's type and the length of its summary, never by fetching the linked
 * page. Fetching would be more accurate in the cases where it worked and silently wrong in the
 * others — paywalls, consent walls and single-page apps all return something that is not the
 * article — and it would add an HTTP call and a failure mode per item. The estimate is crude on
 * purpose, and the rendering says so.
 *
 * @param items   how many stories the estimate covers
 * @param minutes the rounded total
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReadingBudget(int items, int minutes) {

    public static final ReadingBudget NOTHING = new ReadingBudget(0, 0);

    /** Nothing to promise when there is nothing to read. */
    @JsonIgnore
    public boolean isReportable() {
        return items > 0 && minutes > 0;
    }
}
