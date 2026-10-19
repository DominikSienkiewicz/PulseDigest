package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * A quarter of the digest, graded and summarised from what was stored (N20).
 *
 * <p>Where a weekly recap says what moved, this says what it was worth. It is assembled with no
 * live fetch at all — every number comes from editions already in the database — which is what makes
 * it reproducible and what lets it be iterated on outside the four chances a year it would otherwise
 * get.
 *
 * @param from       first day of the window
 * @param to         last day of the window
 * @param editions   how many editions were published in it
 * @param scoreboard the 🔴 badge, graded (N11)
 * @param endorsed   stories the reader voted 👍 on, newest first (N12's signal, surfaced)
 * @param persistent stories that recurred most across the window
 * @param yields     what each source contributed against the slots it was given
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Retrospective(
        LocalDate from,
        LocalDate to,
        int editions,
        PredictionScoreboard scoreboard,
        List<DigestItem> endorsed,
        @JsonProperty("persistent_stories") List<RecurringStory> persistent,
        List<SourceYield> yields
) {

    public Retrospective {
        endorsed = endorsed != null ? List.copyOf(endorsed) : List.of();
        persistent = persistent != null ? List.copyOf(persistent) : List.of();
        yields = yields != null ? List.copyOf(yields) : List.of();
    }

    /** Nothing stored for the window — a quarter with no editions has nothing to say about itself. */
    @JsonIgnore
    public boolean isEmpty() {
        return editions == 0;
    }

    /**
     * One story that kept coming back, and how often.
     *
     * @param title       the story as the most recent edition carrying it rendered it
     * @param url         canonical URL
     * @param appearances editions in the window that carried it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecurringStory(String title, String url, int appearances) {
    }
}
