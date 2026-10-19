package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * The floor under how many sources one edition is allowed to rest on (F10).
 *
 * <p>Owned by the application layer so {@code GenerateMarketReportProcessor} does not import
 * infrastructure-bound configuration types. Wired in
 * {@code market_intel/infrastructure/config/MarketIntelBootstrap} from
 * {@code report.research.min-active-sources}.
 *
 * <p>An absolute count rather than a share of the roster, because a share does not describe what the
 * reader gets: "40% of the sources answered" says nothing, while "two sources answered" says exactly
 * what the edition would have been.
 *
 * <p><strong>Two floors, because breadth collapses in two different ways (N60).</strong>
 * {@code minActiveSources} is measured at the fetch ledger and catches sources that went <em>down</em>.
 * But the ledger records each source's count before the relevance, like-count and recency filters
 * run, so a run in which every source answered and filtering left almost nothing clears it.
 * {@code minContributingSections} is measured on the filtered result — {@code activeSourceCount()},
 * the sections that still carry an item — and catches that.
 *
 * <p><strong>Where this stops and N03 begins.</strong> A quiet news day is not a failure and must not
 * be treated as one: when few stories clear the Must-know bar, the thin-week note ships instead of a
 * padded document, and {@code ThinWeekProperties} calls that trust "the whole asset". That decision is
 * made after synthesis, on stories above the bar. This floor is a different quantity measured earlier:
 * sections carrying any item at all, before a token is spent. A real edition fills sixteen of sixteen
 * (measured on the committed sample), so a floor of three means fourteen sections came back empty
 * while their sources answered — a filter or a lookback window that broke, not a slow week.
 *
 * @param minActiveSources        how many sources must have returned something before the edition is
 *                                worth sending; {@code 0} switches the floor off
 * @param minContributingSections how many sections must still carry an item after filtering;
 *                                {@code 0} switches that floor off
 */
public record SourceFloorPolicy(int minActiveSources, int minContributingSections) {

    public SourceFloorPolicy {
        if (minActiveSources < 0) {
            throw new IllegalArgumentException(
                    "report.research.min-active-sources must not be negative, was " + minActiveSources);
        }
        if (minContributingSections < 0) {
            throw new IllegalArgumentException(
                    "report.research.min-contributing-sections must not be negative, was "
                            + minContributingSections);
        }
    }

    /** Whether this run is held to a floor at all. */
    public boolean enabled() {
        return minActiveSources > 0;
    }

    /**
     * The floor this particular run is held to, capped by the number of sources it attempted.
     *
     * <p>The cap is what keeps a configuration decision from reading as a degradation. Switching a
     * source off (K7) removes it from the roster the run could possibly have, so a run that attempted
     * two sources is asked for two, not three — otherwise an unreachable floor would abort every
     * edition, and the configured number would quietly stop meaning what it says.
     */
    public int effectiveFloor(int attemptedSources) {
        return Math.min(minActiveSources, Math.max(attemptedSources, 0));
    }

    /** Whether this run's breadth is below the floor — the one question the processor asks. */
    public boolean isTooNarrow(int activeSources, int attemptedSources) {
        return enabled() && activeSources < effectiveFloor(attemptedSources);
    }

    /**
     * Whether what survived the filters is too thin to be an edition (N60).
     *
     * <p>Capped by the number of sources that returned anything, and the bound is exact rather than
     * cautious: several sources feed one section (three X searches share "tweets", every RSS feed
     * shares "RSS"), so the sections carrying an item can never outnumber the sources that answered.
     * Asking three sections of a run that had two live sources would therefore be asking for something
     * arithmetically impossible, which is how an unreachable floor aborts every edition — the mistake
     * {@link #effectiveFloor(int)} exists to avoid, in its other form.
     */
    public boolean isTooThin(int contributingSections, int activeSources) {
        int floor = Math.min(minContributingSections, Math.max(activeSources, 0));
        return minContributingSections > 0 && contributingSections < floor;
    }
}
