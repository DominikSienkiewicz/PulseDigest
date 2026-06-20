package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.util.List;

/**
 * Wynik syntezy digest: editorial lead, top-3 insights dnia, lista ocenionych itemów,
 * opcjonalne trendy oraz sygnały z ocenami cross-source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportData(
        @JsonProperty("email_preview") String emailPreview,
        String editorial,
        @JsonProperty("top_insights") List<String> topInsights,
        List<DigestItem> items,
        List<TrendInsight> trends,
        List<Signal> signals
) {

    /** Convenience constructor — no trends, no signals (used by LLM deserialization). */
    public ReportData(String emailPreview, String editorial, List<String> topInsights, List<DigestItem> items) {
        this(emailPreview, editorial, topInsights, items, List.of(), List.of());
    }

    /** Convenience constructor — no signals (used in tests and trend enrichment). */
    public ReportData(String emailPreview, String editorial,
                      List<String> topInsights, List<DigestItem> items, List<TrendInsight> trends) {
        this(emailPreview, editorial, topInsights, items, trends, List.of());
    }

    public ReportData withTrends(List<TrendInsight> newTrends) {
        return new ReportData(emailPreview, editorial, topInsights, items, newTrends, signals);
    }

    public ReportData withSignals(List<Signal> newSignals) {
        return new ReportData(emailPreview, editorial, topInsights, items, trends, newSignals);
    }

    /**
     * Safety-net canonicalization po syntezie LLM — strip-uje tracking params z URL-i itemów.
     * Zwraca {@code this} jeśli nic do zmiany (zachowuje referencyjną tożsamość).
     */
    public ReportData withCanonicalizedUrls() {
        if (items == null || items.isEmpty()) {
            return this;
        }
        List<DigestItem> cleaned = items.stream()
                .map(i -> new DigestItem(
                        i.title(),
                        UrlCanonicalizer.canonicalize(i.url()),
                        i.source(), i.category(), i.type(),
                        i.score(), i.engagementScore(), i.summary(), i.whyItMatters()))
                .toList();
        return new ReportData(emailPreview, editorial, topInsights, cleaned, trends, signals);
    }
}
