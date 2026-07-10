package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * The radar's own track record: of the stories it flagged as Critical candidates, how many actually
 * reached CRITICAL in a later edition.
 *
 * <p>Printed in the footer of every digest. A prediction feature that hides its hit rate asks for
 * trust it has not earned; publishing the number is what makes the 🟠 badge worth reading — and what
 * makes it fair to stop reading it if the number is bad.
 */
public record RadarAccuracy(int flagged, int confirmed) {

    /** No verdict yet: the radar has never flagged a story whose outcome is already known. */
    public boolean hasVerdict() {
        return flagged > 0;
    }

    /** Share of resolved predictions that came true, 0.0 when nothing has been judged yet. */
    public double hitRate() {
        return flagged == 0 ? 0.0 : (double) confirmed / flagged;
    }
}
