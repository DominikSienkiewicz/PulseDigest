package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Deterministic ranking of a signal based on source credibility and cross-source correlation.
 * Ordinal order matches display priority: CRITICAL is shown first.
 */
public enum SignalRank {
    CRITICAL, STRONG, MODERATE, WEAK
}
