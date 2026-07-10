package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * The little a triage model needs to decide whether an item is worth the expensive model's attention:
 * what it is, who said it, and how much the crowd reacted. Deliberately no body text — the whole
 * point of triage is that it is cheap.
 */
public record PreScoringCandidate(String url, String title, String source, int engagement) {
}
