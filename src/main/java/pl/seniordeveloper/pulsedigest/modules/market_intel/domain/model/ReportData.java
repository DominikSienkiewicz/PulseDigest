package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wynik syntezy digest: top-3 insights dnia + lista ocenionych i podsumowanych itemów.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportData(
        @JsonProperty("top_insights") List<String> topInsights,
        List<DigestItem> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DigestItem(
            String title,
            String url,
            String source,
            String category,
            int score,
            String summary
    ) {
    }
}
