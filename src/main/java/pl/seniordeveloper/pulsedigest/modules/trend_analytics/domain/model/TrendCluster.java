package pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model;

import java.util.List;

/**
 * Klaster powtarzających się wątków w jednej kategorii. Pole {@code narrative}
 * jest wypełniane w drugim kroku analizy przez {@code TrendNarrativePort}.
 */
public record TrendCluster(
        String category,
        int occurrences,
        List<String> exampleTitles,
        String narrative
) {

    public TrendCluster withNarrative(String newNarrative) {
        return new TrendCluster(category, occurrences, exampleTitles, newNarrative);
    }
}
