package pl.seniordeveloper.pulsedigest.modules.trend_analytics.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest.DigestSnippet;
import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out.HistoricalDigestPort;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Czyta historyczne raporty z Supabase (Postgres) dla analizy trendów.
 *
 * <p>Świadomie nie współdzielimy modelu z modułem market_intel — kontraktem między modułami
 * jest format JSON payloadu, nie typy domenowe sąsiedniego modułu.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabaseHistoricalDigestAdapter implements HistoricalDigestPort {

    private static final String SELECT_RECENT_SQL = """
            SELECT generated_at, payload FROM reports
            WHERE generated_at > ?
            ORDER BY generated_at DESC
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<HistoricalDigest> fetchRecent(int lookbackDays) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(lookbackDays));
        List<HistoricalDigest> digests = jdbcClient.sql(SELECT_RECENT_SQL)
                .param(OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .query((rs, rowNum) -> {
                    OffsetDateTime generatedAt = rs.getObject("generated_at", OffsetDateTime.class);
                    return mapPayload(generatedAt.toInstant(), rs.getString("payload"));
                })
                .list()
                .stream()
                .flatMap(Optional::stream)
                .toList();
        log.debug("Loaded {} historical digests within last {} days", digests.size(), lookbackDays);
        return digests;
    }

    private Optional<HistoricalDigest> mapPayload(Instant generatedAt, String json) {
        try {
            HistoricalReportDto dto = objectMapper.readValue(json, HistoricalReportDto.class);
            List<DigestSnippet> snippets = dto.report() != null && dto.report().items() != null
                    ? dto.report().items().stream()
                            .map(i -> new DigestSnippet(i.title(), i.category(), i.score()))
                            .toList()
                    : List.of();
            return Optional.of(new HistoricalDigest(generatedAt, snippets));
        } catch (Exception e) {
            log.warn("Skipping malformed historical payload: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HistoricalReportDto(ReportDto report) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReportDto(List<ItemDto> items) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ItemDto(String title, String category, int score) {
        }
    }
}
