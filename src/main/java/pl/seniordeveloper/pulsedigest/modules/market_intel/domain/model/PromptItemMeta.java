package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Trusted metadata of a single item as it was sent to the LLM, keyed elsewhere by canonical URL.
 *
 * <p>The model echoes {@code source} and {@code engagement_score} back in its output, where a typo
 * (or a successful prompt injection) would silently move the item into the wrong credibility bucket
 * and orphan its reader feedback. This record is the input-side truth used to overwrite that echo.
 */
public record PromptItemMeta(String source, int engagementScore) {
}
