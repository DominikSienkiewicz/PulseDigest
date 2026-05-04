package pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.application.error.TrendAnalyticsError;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest.DigestSnippet;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.TrendCluster;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.HistoricalDigestPort;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.TrendNarrativePort;
import pl.seniordeveloper.pulsedigest.shared.result.Result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeTrendsServiceTest {

    private static final AnalyzeTrendsQuery DEFAULT_QUERY = new AnalyzeTrendsQuery(7, 2, 5);

    private StubHistoricalPort historicalPort;
    private StubNarrativePort narrativePort;
    private AnalyzeTrendsService service;

    @BeforeEach
    void setUp() {
        historicalPort = new StubHistoricalPort();
        narrativePort = new StubNarrativePort();
        service = new AnalyzeTrendsService(historicalPort, narrativePort);
    }

    @Test
    void returnsHistoryEmptyWhenNoReports() {
        historicalPort.returns(List.of());

        Result<TrendAnalysisView, TrendAnalyticsError> result = service.handle(DEFAULT_QUERY);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.getError()).isInstanceOf(TrendAnalyticsError.HistoryEmpty.class);
    }

    @Test
    void returnsNoSignificantTrendsWhenAllCategoriesBelowThreshold() {
        historicalPort.returns(List.of(
                digest(Instant.now(), snippet("A", "Security/Privacy", 5)),
                digest(Instant.now(), snippet("B", "Java/JVM", 3))
        ));

        Result<TrendAnalysisView, TrendAnalyticsError> result = service.handle(DEFAULT_QUERY);

        assertThat(result.getError()).isInstanceOf(TrendAnalyticsError.NoSignificantTrends.class);
    }

    @Test
    void groupsByCategoryAndFiltersBelowMinOccurrences() {
        historicalPort.returns(List.of(
                digest(Instant.now(), snippet("CVE-1", "Security/Privacy", 9)),
                digest(Instant.now(), snippet("CVE-2", "Security/Privacy", 8)),
                digest(Instant.now(), snippet("CVE-3", "Security/Privacy", 7)),
                digest(Instant.now(), snippet("Solo", "Java/JVM", 5))
        ));
        narrativePort.returns(Map.of("Security/Privacy", "Security shipping fast"));

        Result<TrendAnalysisView, TrendAnalyticsError> result = service.handle(DEFAULT_QUERY);

        assertThat(result).isInstanceOf(Result.Success.class);
        List<TrendCluster> clusters = result.getValue().clusters();
        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().category()).isEqualTo("Security/Privacy");
        assertThat(clusters.getFirst().occurrences()).isEqualTo(3);
    }

    @Test
    void picksTopExampleTitlesByScoreDesc() {
        historicalPort.returns(List.of(
                digest(Instant.now(),
                        snippet("low", "AI/LLM", 1),
                        snippet("mid", "AI/LLM", 5),
                        snippet("hi", "AI/LLM", 9),
                        snippet("hi-2", "AI/LLM", 8))
        ));
        narrativePort.returns(Map.of("AI/LLM", "n"));

        var clusters = service.handle(DEFAULT_QUERY).getValue().clusters();

        assertThat(clusters.getFirst().exampleTitles())
                .containsExactly("hi", "hi-2", "mid");
    }

    @Test
    void sortsClustersByOccurrencesDescAndCapsAtMaxClusters() {
        historicalPort.returns(List.of(
                digest(Instant.now(),
                        snippet("a1", "Cat-A", 5),
                        snippet("a2", "Cat-A", 5),
                        snippet("b1", "Cat-B", 5),
                        snippet("b2", "Cat-B", 5),
                        snippet("b3", "Cat-B", 5),
                        snippet("c1", "Cat-C", 5),
                        snippet("c2", "Cat-C", 5),
                        snippet("c3", "Cat-C", 5),
                        snippet("c4", "Cat-C", 5))
        ));
        narrativePort.returns(Map.of());

        var query = new AnalyzeTrendsQuery(7, 2, 2);
        var clusters = service.handle(query).getValue().clusters();

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).category()).isEqualTo("Cat-C");
        assertThat(clusters.get(1).category()).isEqualTo("Cat-B");
    }

    @Test
    void enrichesClustersWithNarratives() {
        historicalPort.returns(List.of(
                digest(Instant.now(),
                        snippet("x", "Java/JVM", 7),
                        snippet("y", "Java/JVM", 6))
        ));
        narrativePort.returns(Map.of("Java/JVM", "JVM stack moves fast this week"));

        var clusters = service.handle(DEFAULT_QUERY).getValue().clusters();

        assertThat(clusters.getFirst().narrative()).isEqualTo("JVM stack moves fast this week");
    }

    @Test
    void leavesNarrativeBlankWhenLlmReturnsNoEntryForCategory() {
        historicalPort.returns(List.of(
                digest(Instant.now(),
                        snippet("x", "DevOps", 5),
                        snippet("y", "DevOps", 5))
        ));
        narrativePort.returns(Map.of());

        var clusters = service.handle(DEFAULT_QUERY).getValue().clusters();

        assertThat(clusters.getFirst().narrative()).isEmpty();
    }

    @Test
    void ignoresSnippetsWithBlankCategory() {
        historicalPort.returns(List.of(
                digest(Instant.now(),
                        snippet("x", "AI/LLM", 5),
                        snippet("y", "AI/LLM", 5),
                        snippet("noCat", null, 5),
                        snippet("noCat2", "  ", 5))
        ));
        narrativePort.returns(Map.of("AI/LLM", "n"));

        var clusters = service.handle(DEFAULT_QUERY).getValue().clusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().occurrences()).isEqualTo(2);
    }

    private static HistoricalDigest digest(Instant at, DigestSnippet... snippets) {
        return new HistoricalDigest(at, List.of(snippets));
    }

    private static DigestSnippet snippet(String title, String category, int score) {
        return new DigestSnippet(title, category, score);
    }

    private static final class StubHistoricalPort implements HistoricalDigestPort {
        private List<HistoricalDigest> digests = List.of();

        void returns(List<HistoricalDigest> ds) {
            this.digests = ds;
        }

        @Override
        public List<HistoricalDigest> fetchRecent(int lookbackDays) {
            return digests;
        }
    }

    private static final class StubNarrativePort implements TrendNarrativePort {
        private Map<String, String> narratives = Map.of();

        void returns(Map<String, String> n) {
            this.narratives = n;
        }

        @Override
        public Map<String, String> narrateBatch(List<TrendCluster> clusters) {
            return narratives;
        }
    }
}
