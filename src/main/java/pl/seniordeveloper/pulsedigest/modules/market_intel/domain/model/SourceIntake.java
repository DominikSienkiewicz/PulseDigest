package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * What a source actually delivered into the pipeline over the history window, counted from the
 * per-edition fetch reports rather than from what got published.
 *
 * <p>This is the denominator {@link SourceYield} never had. The yield ledger tallies sources that
 * appear in an edition's signals, so a source returning an empty result set has no entry at all and
 * reads as a source that does not exist (F7). An intake row exists for every source the run
 * touched, including the ones that returned nothing — which is the only way "this source has gone
 * quiet" can be distinguished from "this source was never configured".
 *
 * @param source         the fetch-side source name, as {@code MarketResearchService} labels the call
 * @param editions       editions in the window in which this source was attempted
 * @param silentEditions how many of those returned zero items
 * @param itemsFetched   total items the source returned across the window
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record SourceIntake(String source, int editions, int silentEditions, int itemsFetched) {

    /**
     * A source attempted repeatedly that has never once returned an item in this window.
     *
     * <p>{@code @JsonIgnore} because this record became part of the stored payload when N27 put
     * silent sources on {@link ReportData}: Jackson would serialize the derived accessor as a field
     * no constructor can read back, which makes the whole payload undeserializable rather than
     * merely larger.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isSilent() {
        return editions > 0 && itemsFetched == 0;
    }
}
