package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * What a source actually returned on its prompt slots over the history window: how often its items
 * were published at all, and how often they earned a high rank.
 *
 * <p>Prompt budget is allocated statically today, in the dark. This is the measurement that has to
 * exist before anyone can argue about reallocating it — or about adding a seventeenth source.
 */
public record SourceYield(String source, int appearances, int highRankAppearances, double yieldRatio) {
}
