package pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Projekcja zarchiwizowanego raportu zredukowana do pól potrzebnych w analizie trendów —
 * tylko (title, category, score) per item plus znacznik czasu.
 *
 * <p>Świadomie nie współdzielimy modelu z modułem market_intel — kontraktem między modułami
 * jest format zapisu (JSON / wiersz w Postgres), nie typy domenowe.
 */
public record HistoricalDigest(
        Instant generatedAt,
        List<DigestSnippet> items
) {

    public record DigestSnippet(String title, String category, int score) {
    }
}
