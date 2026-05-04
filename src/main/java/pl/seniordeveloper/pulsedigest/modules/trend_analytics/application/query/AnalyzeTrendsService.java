package pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.error.TrendAnalyticsError;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest.DigestSnippet;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.HistoricalDigestPort;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.TrendNarrativePort;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frequency-based trend detection across historical digests, enriched with LLM narratives.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AnalyzeTrendsService {

    private static final int MAX_EXAMPLES_PER_CLUSTER = 3;

    private final HistoricalDigestPort historicalPort;
    private final TrendNarrativePort narrativePort;

    public Result<TrendAnalysisView, TrendAnalyticsError> handle(AnalyzeTrendsQuery query) {
        List<HistoricalDigest> history = historicalPort.fetchRecent(query.lookbackDays());
        if (history.isEmpty()) {
            log.info("Trend analysis: no historical digests in last {} days", query.lookbackDays());
            return Result.failure(new TrendAnalyticsError.HistoryEmpty());
        }

        Map<String, List<DigestSnippet>> byCategory = history.stream()
                .flatMap(d -> d.items().stream())
                .filter(s -> s.category() != null && !s.category().isBlank())
                .collect(Collectors.groupingBy(DigestSnippet::category));

        List<TrendCluster> rawClusters = byCategory.entrySet().stream()
                .filter(e -> e.getValue().size() >= query.minOccurrences())
                .map(e -> new TrendCluster(
                        e.getKey(),
                        e.getValue().size(),
                        topTitlesByScore(e.getValue()),
                        ""))
                .sorted(Comparator.comparingInt(TrendCluster::occurrences).reversed())
                .limit(query.maxClusters())
                .toList();

        if (rawClusters.isEmpty()) {
            log.info("Trend analysis: no category passed minOccurrences={} threshold", query.minOccurrences());
            return Result.failure(new TrendAnalyticsError.NoSignificantTrends(
                    query.lookbackDays(), query.minOccurrences()));
        }

        Map<String, String> narratives = narrativePort.narrateBatch(rawClusters);

        List<TrendCluster> enriched = rawClusters.stream()
                .map(c -> c.withNarrative(narratives.getOrDefault(c.category(), "")))
                .toList();

        log.info("Trend analysis: detected {} clusters across {} days", enriched.size(), query.lookbackDays());
        return Result.success(new TrendAnalysisView(enriched));
    }

    private static List<String> topTitlesByScore(List<DigestSnippet> snippets) {
        return snippets.stream()
                .sorted(Comparator.comparingInt(DigestSnippet::score).reversed())
                .limit(MAX_EXAMPLES_PER_CLUSTER)
                .map(DigestSnippet::title)
                .toList();
    }
}
