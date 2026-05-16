package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DbEngineRanking;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DbEnginesProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DbEnginesAdapterTest {

    private static final String RANKING_HTML = """
            <html><body><table class="dbi">
            <tr><th>Rank</th><th>DBMS</th><th>Score</th><th>Mo. change</th><th>Year change</th></tr>
            <tr><td>1.</td><td><a href="/en/ranking/postgresql">PostgreSQL</a></td>
            <td>1250.5</td><td>+12.3</td><td>-2.1</td></tr>
            <tr><td>2.</td><td><a href="/en/ranking/mysql">MySQL</a></td>
            <td>1200.0</td><td>-5.0</td><td>+1.0</td></tr>
            <tr><td>5.</td><td><a href="/en/ranking/duckdb">DuckDB</a></td>
            <td>500.0</td><td>+25.0</td><td>+3.0</td></tr>
            </table></body></html>
            """;

    private DbEnginesAdapter adapter;

    @BeforeEach
    void setUp() {
        DbEnginesProperties props =
                new DbEnginesProperties(
                        "https://db-engines.com/en/ranking",
                        7,
                        5
                );
        adapter = new DbEnginesAdapter(props, new ObjectMapper());
    }

    @Test
    void detectsSignificantScoreChanges() {
        List<DbEngineRanking> rankings = adapter.parseRankings(RANKING_HTML);

        assertThat(rankings).hasSize(3);
        assertThat(rankings.get(0).dbName()).isEqualTo("PostgreSQL");
        assertThat(rankings.get(0).rank()).isEqualTo(1);
        assertThat(rankings.get(0).scoreChange()).isEqualTo(12.3);
        assertThat(rankings.get(0).rankChange()).isEqualTo(0);
        assertThat(rankings.get(1).dbName()).isEqualTo("MySQL");
        assertThat(rankings.get(1).scoreChange()).isEqualTo(-5.0);
        assertThat(rankings.get(2).dbName()).isEqualTo("DuckDB");
        assertThat(rankings.get(2).scoreChange()).isEqualTo(25.0);
    }

    @Test
    void returnsEmptyListForUnparseableHtml() {
        List<DbEngineRanking> rankings = adapter.parseRankings("<div>Not a table</div>");
        assertThat(rankings).isEmpty();
    }
}
