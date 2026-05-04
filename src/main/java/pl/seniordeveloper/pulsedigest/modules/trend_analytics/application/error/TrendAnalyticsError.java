package pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.error;

import pl.seniordeveloper.pulsedigest.shared.error.DomainError;

/**
 * Sealed hierarchy of trend-analytics errors.
 */
public sealed interface TrendAnalyticsError extends DomainError
        permits TrendAnalyticsError.HistoryEmpty,
        TrendAnalyticsError.NoSignificantTrends,
        TrendAnalyticsError.NarrativeGenerationFailed {

    default String code() {
        return "TREND_ANALYTICS_ERROR";
    }

    record HistoryEmpty() implements TrendAnalyticsError {
        public String message() {
            return "No historical reports available in the lookback window";
        }
    }

    record NoSignificantTrends(int lookbackDays, int minOccurrences) implements TrendAnalyticsError {
        public String message() {
            return "No category reached the minOccurrences=" + minOccurrences
                    + " threshold within the last " + lookbackDays + " days";
        }
    }

    record NarrativeGenerationFailed(String reason) implements TrendAnalyticsError {
        public String message() {
            return "Failed to generate trend narratives: " + reason;
        }
    }
}
