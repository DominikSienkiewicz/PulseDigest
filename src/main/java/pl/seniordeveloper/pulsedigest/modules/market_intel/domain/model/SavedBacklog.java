package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What the reader has kept and not come back to — the shelf, and how full it really is.
 *
 * <p>Carries the total separately from the items because the mail shows only the oldest few: a
 * section that silently truncates teaches the reader that the number it shows is the number there
 * is, and the first time they discover otherwise is the last time they trust it.
 *
 * @param shown the items the mail will render, oldest first
 * @param total how many are on the shelf altogether
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavedBacklog(List<SavedItem> shown, int total) {

    public SavedBacklog {
        shown = shown != null ? List.copyOf(shown) : List.of();
    }

    /** An empty shelf — the normal state, and the one that renders nothing at all. */
    public static SavedBacklog empty() {
        return new SavedBacklog(List.of(), 0);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return shown.isEmpty();
    }

    /** How many are on the shelf beyond the ones rendered. */
    @JsonIgnore
    public int remaining() {
        return Math.max(0, total - shown.size());
    }
}
