package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wynik syntezy digest: editorial lead, top-3 insights dnia, lista ocenionych itemów
 * oraz opcjonalne trendy z analizy historycznych raportów.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportData(
        @JsonProperty("email_preview") String emailPreview,
        String editorial,
        @JsonProperty("top_insights") List<String> topInsights,
        List<DigestItem> items,
        List<TrendInsight> trends
) {

    public ReportData(String emailPreview, String editorial, List<String> topInsights, List<DigestItem> items) {
        this(emailPreview, editorial, topInsights, items, List.of());
    }

    public ReportData withTrends(List<TrendInsight> newTrends) {
        return new ReportData(emailPreview, editorial, topInsights, items, newTrends);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DigestItem(
            String title,
            String url,
            String source,
            String category,
            String type,
            int score,
            @JsonProperty("engagement_score") Integer engagementScore,
            String summary
    ) {
    }
}
