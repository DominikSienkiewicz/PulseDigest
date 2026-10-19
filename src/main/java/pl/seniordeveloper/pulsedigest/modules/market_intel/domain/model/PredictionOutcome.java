package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * What became of one story the digest called 🔴 CRITICAL (N11).
 *
 * <p>The outcome is derived from stored editions and stored votes — never from a fresh judgement —
 * so the grade cannot be softened after the fact by the thing being graded.
 *
 * @param title            the story as the edition that flagged it rendered it
 * @param url              canonical URL, the key everything else is matched on
 * @param flaggedAt        when the edition that called it CRITICAL was generated
 * @param laterAppearances how many later editions carried the same story
 * @param upvoted          whether the reader voted 👍 on it at any point
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictionOutcome(
        String title,
        String url,
        @JsonProperty("flagged_at") Instant flaggedAt,
        @JsonProperty("later_appearances") int laterAppearances,
        boolean upvoted
) {

    /**
     * Whether the call held up.
     *
     * <p>Two independent kinds of evidence, deliberately not weighted against each other: the story
     * kept being carried by sources after the flag, or the reader said it mattered. Either is enough.
     * Requiring both would grade the badge on the reader's clicking habits as much as on the badge.
     */
    @JsonIgnore
    public boolean heldUp() {
        return laterAppearances > 0 || upvoted;
    }

    /**
     * How the quarterly section labels it.
     *
     * <p>These four strings reach a reader, so they are written as the characters they are. Until
     * 2026-10-19 three of them carried doubled escapes — {@code "trzyma\\u0142o"} rather than
     * {@code "trzymało"} — and the quarterly mail printed the escape sequences verbatim. Nothing
     * caught it because {@code RetrospectiveEmailBuilder} had no test: the only gate on this path was
     * JaCoCo's instruction count, which this method satisfied by being called at all.
     */
    @JsonIgnore
    public String verdict() {
        if (laterAppearances > 0 && upvoted) {
            return "trzymało się i zostało docenione";
        }
        if (laterAppearances > 0) {
            return "wracało w kolejnych wydaniach";
        }
        if (upvoted) {
            return "doceniona przez Ciebie";
        }
        return "zgasło bez śladu";
    }
}
