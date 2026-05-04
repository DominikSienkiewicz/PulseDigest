package pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;

import java.util.List;
import java.util.Map;

/**
 * Port generowania narracji dla klastrów trendów. Implementacja powinna wykonać
 * pojedynczy batchowy LLM call dla wszystkich klastrów (oszczędność kosztu vs N callów).
 *
 * <p>Zwraca mapę {@code category → narrative}. Brak wpisu dla danej kategorii
 * oznacza graceful failure — caller pozostawia pusty narrative.
 */
public interface TrendNarrativePort {

    Map<String, String> narrateBatch(List<TrendCluster> clusters);
}
