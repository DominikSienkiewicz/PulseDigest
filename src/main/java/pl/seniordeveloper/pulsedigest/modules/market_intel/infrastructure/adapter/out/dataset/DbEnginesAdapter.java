package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.http.ExternalRestClients;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DbEngineRanking;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks significant database ranking movements from DB-Engines.com.
 * The ranking updates monthly; this adapter returns only significant movers.
 */
@Slf4j
@Service
public class DbEnginesAdapter {

    private static final Pattern TABLE_ROW = Pattern.compile(
            "<tr[^>]*>.*?</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RANK_CELL = Pattern.compile(
            "<td[^>]*>\\s*(\\d+)\\s*\\.\\s*</td>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_CELL = Pattern.compile(
            "<a[^>]*href=\"[^\"]*\">\\s*(.+?)\\s*</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_CELL = Pattern.compile(
            "<td[^>]*>\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*</td>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANGE_CELL = Pattern.compile(
            "<td[^>]*>\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*</td>", Pattern.CASE_INSENSITIVE);

    private final ReportProperties.DbEnginesProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public DbEnginesAdapter(ReportProperties reportProperties, ObjectMapper objectMapper) {
        this(reportProperties.dbEngines(), objectMapper);
    }

    DbEnginesAdapter(ReportProperties.DbEnginesProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.restClient = ExternalRestClients.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "text/html, application/xhtml+xml")
                .defaultHeader("User-Agent", "PulseDigest/1.0")
                .build();
    }

    public List<DbEngineRanking> fetchDbEngineRankings() {
        try {
            String html = restClient.get()
                    .retrieve()
                    .body(String.class);
            if (html == null || html.isBlank()) {
                return List.of();
            }
            List<DbEngineRanking> rankings = parseRankings(html);
            log.info("DB-Engines: {} significant movers (minScoreChange={})",
                    rankings.size(), properties.minScoreChange());
            return rankings;
        } catch (Exception e) {
            log.warn("DB-Engines fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<DbEngineRanking> parseRankings(String html) {
        try {
            List<DbEngineRanking> result = new ArrayList<>();
            Matcher rowMatcher = TABLE_ROW.matcher(html);
            int rank = 0;
            while (rowMatcher.find()) {
                String row = rowMatcher.group();
                Matcher rankMatch = RANK_CELL.matcher(row);
                if (!rankMatch.find()) {
                    continue;
                }
                rank = Integer.parseInt(rankMatch.group(1));
                String name = "";
                Matcher nameMatch = NAME_CELL.matcher(row);
                if (nameMatch.find()) {
                    name = nameMatch.group(1).trim();
                }
                if (name.isBlank()) {
                    continue;
                }
                List<String> numbers = new ArrayList<>();
                Matcher scoreMatcher = SCORE_CELL.matcher(row);
                while (scoreMatcher.find() && numbers.size() < 2) {
                    numbers.add(scoreMatcher.group(1));
                }
                if (numbers.size() < 2) {
                    continue;
                }
                double score = Double.parseDouble(numbers.get(0));
                double scoreChange = parseDoubleSafe(numbers.get(1));
                if (Math.abs(scoreChange) < properties.minScoreChange()) {
                    continue;
                }
                String url = "https://db-engines.com/en/ranking/"
                        + name.toLowerCase().replace(' ', '+');
                result.add(new DbEngineRanking(name, rank, 0, score, scoreChange,
                        url, LocalDateTime.now()));
            }
            return result;
        } catch (Exception e) {
            log.warn("DB-Engines parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.replace("+", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
