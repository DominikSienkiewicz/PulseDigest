package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.seniordeveloper.pulsedigest.shared.util.UrlCanonicalizer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wynik syntezy digest: editorial lead, top-3 insights dnia, lista ocenionych itemów
 * oraz sygnały z ocenami cross-source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportData(
        @JsonProperty("email_preview") String emailPreview,
        String editorial,
        @JsonProperty("top_insights") List<String> topInsights,
        List<DigestItem> items,
        List<Signal> signals,
        @JsonProperty("weekly_recap") WeeklyRecap weeklyRecap,
        @JsonProperty("radar_accuracy") RadarAccuracy radarAccuracy
) {

    /** Convenience constructor — no signals (used by LLM deserialization and tests). */
    public ReportData(String emailPreview, String editorial, List<String> topInsights, List<DigestItem> items) {
        this(emailPreview, editorial, topInsights, items, List.of(), null, null);
    }

    /** Convenience constructor — scored but without a weekly recap (non-Friday editions and tests). */
    public ReportData(String emailPreview, String editorial, List<String> topInsights,
                      List<DigestItem> items, List<Signal> signals) {
        this(emailPreview, editorial, topInsights, items, signals, null, null);
    }

    public ReportData withSignals(List<Signal> newSignals) {
        return new ReportData(emailPreview, editorial, topInsights, items, newSignals, weeklyRecap, radarAccuracy);
    }

    /** Copy of this report carrying the Friday "week in signals" block. */
    public ReportData withWeeklyRecap(WeeklyRecap recap) {
        return new ReportData(emailPreview, editorial, topInsights, items, signals, recap, radarAccuracy);
    }

    /** Copy of this report carrying the predictive radar's published hit rate. */
    public ReportData withRadarAccuracy(RadarAccuracy accuracy) {
        return new ReportData(emailPreview, editorial, topInsights, items, signals, weeklyRecap, accuracy);
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
                        i.score(), i.engagementScore(), i.summary(), i.whyItMatters(), i.topicKey()))
                .toList();
        return new ReportData(emailPreview, editorial, topInsights, cleaned, signals, weeklyRecap, radarAccuracy);
    }

    /**
     * Re-joins each item with the trusted metadata of the prompt item carrying the same canonical URL.
     * The model's echoed {@code source} and {@code engagement_score} are overwritten, and any item
     * whose URL was never in the prompt — a hallucination, or an exfiltration attempt riding on a
     * successful prompt injection — is dropped.
     */
    public ReportData withRejoinedMetadata(Map<String, PromptItemMeta> inputMeta) {
        if (items == null || items.isEmpty()) {
            return this;
        }
        List<DigestItem> rejoined = items.stream()
                .map(item -> rejoin(item, inputMeta))
                .filter(Objects::nonNull)
                .toList();
        return new ReportData(emailPreview, editorial, topInsights, rejoined, signals, weeklyRecap, radarAccuracy);
    }

    private static DigestItem rejoin(DigestItem item, Map<String, PromptItemMeta> inputMeta) {
        if (item.url() == null || item.url().isBlank()) {
            return null;
        }
        String canonical = UrlCanonicalizer.canonicalize(item.url());
        PromptItemMeta meta = inputMeta.get(canonical);
        if (meta == null) {
            return null;
        }
        return new DigestItem(item.title(), canonical, meta.source(), item.category(), item.type(),
                item.score(), meta.engagementScore(), item.summary(), item.whyItMatters(), item.topicKey());
    }
}
