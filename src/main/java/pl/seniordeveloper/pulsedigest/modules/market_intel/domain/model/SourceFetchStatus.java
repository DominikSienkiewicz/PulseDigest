package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * How a source's fetch ended.
 *
 * <p>{@code DISABLED} is not a failure and not a silence: it is a source somebody switched off
 * (N27). Keeping it distinct matters because the intake ledger counts silent sources to decide
 * which ones look dead, and a source that is off would otherwise look like a source that is
 * reachable and returning nothing — the two call for opposite actions.
 */
public enum SourceFetchStatus {
    SUCCESS,
    FAILED,
    DISABLED
}
