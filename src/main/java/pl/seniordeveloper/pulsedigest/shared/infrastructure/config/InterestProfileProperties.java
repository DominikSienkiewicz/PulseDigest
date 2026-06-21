package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Single source of truth for the reader's interest profile. Replaces the persona and relevance
 * keywords that used to be hardcoded in three places (the system prompt, the tech-demand narrator,
 * and {@code MarketResearchService}). The {@code persona} is injected into both LLM prompts; the
 * {@code relevanceKeywords} drive the client-side tweet relevance filter (via {@code ResearchPolicy}).
 */
@ConfigurationProperties(prefix = "interest-profile")
@Validated
public record InterestProfileProperties(
        @NotBlank String persona,
        @NotEmpty List<@NotBlank String> relevanceKeywords
) {
}
