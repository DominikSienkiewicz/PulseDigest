package pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query;

/**
 * Parametry analizy trendów.
 *
 * @param lookbackDays    okno historii (np. 7 dni wstecz)
 * @param minOccurrences  próg wystąpień by kategoria zakwalifikowała się jako trend
 * @param maxClusters     maksymalna liczba zwracanych klastrów (top-K)
 */
public record AnalyzeTrendsQuery(int lookbackDays, int minOccurrences, int maxClusters) {
}
