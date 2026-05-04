package pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query;

import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;

import java.util.List;

/**
 * Wynik analizy trendów: top-K klastrów z narracjami.
 */
public record TrendAnalysisView(List<TrendCluster> clusters) {
}
