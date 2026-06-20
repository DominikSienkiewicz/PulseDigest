package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single item in a synthesized digest report, as produced by the LLM.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DigestItem(
        String title,
        String url,
        String source,
        String category,
        String type,
        int score,
        @JsonProperty("engagement_score") Integer engagementScore,
        String summary,
        @JsonProperty("why_it_matters") String whyItMatters
) {
}
