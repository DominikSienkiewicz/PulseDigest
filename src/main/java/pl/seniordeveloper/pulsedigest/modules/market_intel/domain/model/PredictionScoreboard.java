package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The digest grading its own 🔴 badge over a quarter (N11).
 *
 * <p>The rarest thing a project like this can publish: its own past output, scored, including when
 * the score is bad. {@code RadarAccuracy} and {@code TrendMemory} already measured the pieces;
 * this is the view that makes them answerable to the reader.
 *
 * @param outcomes      one per CRITICAL story old enough to have an outcome, newest first
 * @param windowDays    how far back the editions were read
 * @param maturityDays  how long a story must have had to prove itself before it is graded at all
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictionScoreboard(
        List<PredictionOutcome> outcomes,
        @JsonProperty("window_days") int windowDays,
        @JsonProperty("maturity_days") int maturityDays
) {

    public PredictionScoreboard {
        outcomes = outcomes != null ? List.copyOf(outcomes) : List.of();
    }

    public static PredictionScoreboard empty(int windowDays, int maturityDays) {
        return new PredictionScoreboard(List.of(), windowDays, maturityDays);
    }

    @JsonIgnore
    public int graded() {
        return outcomes.size();
    }

    @JsonIgnore
    public long heldUp() {
        return outcomes.stream().filter(PredictionOutcome::heldUp).count();
    }

    /**
     * Whether there is enough here to report a rate at all. Three is the same floor the calibration
     * line uses: below it a single story swings the number by a third, and a percentage that moves
     * that far on one data point reads as precision it does not have.
     */
    @JsonIgnore
    public boolean isReportable() {
        return graded() >= 3;
    }

    /** Share of graded calls that held up, 0–100. */
    @JsonIgnore
    public int hitRatePercent() {
        return graded() == 0 ? 0 : (int) Math.round(heldUp() * 100.0 / graded());
    }
}
