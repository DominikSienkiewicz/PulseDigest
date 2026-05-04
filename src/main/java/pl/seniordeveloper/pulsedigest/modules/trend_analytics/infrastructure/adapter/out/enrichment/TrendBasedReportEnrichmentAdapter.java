package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.enrichment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendInsight;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportEnrichmentPort;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query.AnalyzeTrendsQuery;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query.AnalyzeTrendsService;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.util.List;

/**
 * Spina trend_analytics z market_intel: implementuje port wzbogacenia raportu.
 * Przy braku danych historycznych albo błędzie LLM zwraca raport bez zmian
 * (analiza trendów nigdy nie blokuje wysyłki maila).
 *
 * <p>Włączane flagą {@code report.trend.enabled} (domyślnie aktywne).
 */
@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(
        prefix = "report.trend",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TrendBasedReportEnrichmentAdapter implements ReportEnrichmentPort {

    private final AnalyzeTrendsService trendsService;
    private final ReportProperties reportProperties;

    @Override
    public ReportData enrich(ReportData report) {
        ReportProperties.TrendProperties props = reportProperties.trend();
        if (props == null) {
            log.debug("Trend properties not configured — skipping enrichment");
            return report;
        }
        AnalyzeTrendsQuery query = new AnalyzeTrendsQuery(
                props.lookbackDays(), props.minOccurrences(), props.maxClusters());

        return trendsService.handle(query).fold(
                view -> report.withTrends(toInsights(view.clusters())),
                error -> {
                    log.info("Skipping trend section: {}", error.message());
                    return report;
                }
        );
    }

    private static List<TrendInsight> toInsights(List<TrendCluster> clusters) {
        return clusters.stream()
                .map(c -> new TrendInsight(
                        c.category(),
                        c.occurrences(),
                        c.narrative(),
                        c.exampleTitles()))
                .toList();
    }
}
