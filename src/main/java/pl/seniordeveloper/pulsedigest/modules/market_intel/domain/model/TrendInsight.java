package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Pojedynczy trend wykryty w historycznych raportach: kategoria powtarzająca się N razy
 * w oknie analizy, z opcjonalną narracją (LLM-generated) i przykładowymi tytułami.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrendInsight(
        String category,
        int occurrences,
        String narrative,
        @JsonProperty("example_titles") List<String> exampleTitles
) {
}
