package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/**
 * Single item in a synthesized digest report, as produced by the LLM.
 *
 * <p>{@code topicKey} is the slug of the underlying story ({@code "model-context-protocol"}), which
 * is what cross-source correlation actually needs: {@code category} is an umbrella ("AI/LLM") broad
 * enough that unrelated stories share it, so grouping by category measured category diversity rather
 * than one story being confirmed independently.
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
        @JsonProperty("why_it_matters") String whyItMatters,
        @JsonProperty("topic_key") String topicKey
) {

    /** Convenience constructor for items with no topic key (legacy editions and tests). */
    public DigestItem(String title, String url, String source, String category, String type,
                      int score, Integer engagementScore, String summary, String whyItMatters) {
        this(title, url, source, category, type, score, engagementScore, summary, whyItMatters, null);
    }

    /**
     * The unit cross-source correlation is measured over. Normally the story slug; when the model
     * omitted it, the broad category, so a degraded response falls back to the old behaviour instead
     * of silently switching Critical Trends off. Blank when the item carries neither.
     */
    @JsonIgnore
    public String correlationKey() {
        String key = topicKey != null && !topicKey.isBlank() ? topicKey : category;
        return key != null ? key.strip().toLowerCase(Locale.ROOT) : "";
    }
}
