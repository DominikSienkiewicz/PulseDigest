package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What a second opinion would have removed from an edition (N23).
 *
 * <p>Scoring sees items one at a time. The failure it cannot see is an edition where every item is
 * individually defensible and the whole thing is boring — which is the failure a reader notices
 * first and a rubric never reports.
 *
 * @param cuts items the critique would drop, each with the reason it gave
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CritiqueVerdict(List<Cut> cuts) {

    public static final CritiqueVerdict NOTHING = new CritiqueVerdict(List.of());

    public CritiqueVerdict {
        cuts = cuts != null ? List.copyOf(cuts) : List.of();
    }

    /**
     * One proposed removal.
     *
     * @param url    the item, matched on its canonical URL
     * @param reason why the critique says it does not earn its place, in its own words
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cut(String url, String reason) {
    }
}
