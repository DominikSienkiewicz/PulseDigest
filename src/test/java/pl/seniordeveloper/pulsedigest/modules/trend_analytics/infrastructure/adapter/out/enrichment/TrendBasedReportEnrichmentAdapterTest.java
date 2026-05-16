package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.enrichment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.error.TrendAnalyticsError;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query.AnalyzeTrendsQuery;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query.AnalyzeTrendsService;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query.TrendAnalysisView;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.HistoricalDigestPort;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.TrendNarrativePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TrendProperties;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrendBasedReportEnrichmentAdapterTest {

    private static final ReportData BASE_REPORT = new ReportData(
            "preview", "editorial", List.of("insight"), List.of(), List.of());
    private static final TrendProperties DEFAULT_PROPS = new TrendProperties(true, 7, 2, 5);

    private TrendBasedReportEnrichmentAdapter adapter;
    private FixedAnalyzeTrendsService trendsService;

    @BeforeEach
    void setUp() {
        trendsService = new FixedAnalyzeTrendsService();
        adapter = new TrendBasedReportEnrichmentAdapter(trendsService, DEFAULT_PROPS);
    }

    @Test
    void appendsTrendsOnSuccess() {
        trendsService.willReturn(Result.success(new TrendAnalysisView(List.of(
                new TrendCluster("Security/Privacy", 3, List.of("CVE-1", "CVE-2"), "Trzeci dzień CVE")
        ))));

        ReportData enriched = adapter.enrich(BASE_REPORT);

        assertThat(enriched.trends()).hasSize(1);
        assertThat(enriched.trends().getFirst().category()).isEqualTo("Security/Privacy");
        assertThat(enriched.trends().getFirst().narrative()).isEqualTo("Trzeci dzień CVE");
    }

    @Test
    void returnsOriginalReportWhenAnalysisFails() {
        trendsService.willReturn(Result.failure(new TrendAnalyticsError.HistoryEmpty()));

        ReportData enriched = adapter.enrich(BASE_REPORT);

        assertThat(enriched).isSameAs(BASE_REPORT);
    }

    private static final class FixedAnalyzeTrendsService extends AnalyzeTrendsService {

        private Result<TrendAnalysisView, TrendAnalyticsError> response;

        private FixedAnalyzeTrendsService() {
            super(new EmptyHistoricalPort(), new EmptyNarrativePort());
        }

        void willReturn(Result<TrendAnalysisView, TrendAnalyticsError> r) {
            this.response = r;
        }

        @Override
        public Result<TrendAnalysisView, TrendAnalyticsError> handle(AnalyzeTrendsQuery query) {
            return response;
        }
    }

    private static final class EmptyHistoricalPort implements HistoricalDigestPort {
        @Override
        public List<HistoricalDigest> fetchRecent(int lookbackDays) {
            return List.of();
        }
    }

    private static final class EmptyNarrativePort implements TrendNarrativePort {
        @Override
        public Map<String, String> narrateBatch(List<TrendCluster> clusters) {
            return Map.of();
        }
    }
}
