package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration for the tech-demand pulse derived from the monthly HN "Who is hiring?" thread.
 *
 * @param enabled         master switch; when false the source is skipped entirely
 * @param baseUrl         Algolia HN API base (e.g. {@code https://hn.algolia.com/api/v1}); the
 *                        adapter appends {@code /search} and {@code /items/{id}} itself
 * @param lookbackDays    only render when the source thread was posted within this many days
 * @param maxComments     cap on hiring posts (comments) analyzed, to bound parsing cost
 * @param maxTechnologies how many top technologies to show in the ranking
 * @param minMentions     ignore technologies mentioned in fewer than this many posts
 * @param technologies    vocabulary of technology tokens to count
 * @param priorityTechnologies the reader's own stack, shown on the "your stack" line regardless of
 *                        ranking; should be a subset of {@code technologies}
 */
@ConfigurationProperties(prefix = "report.tech-demand")
@Validated
public record TechDemandProperties(
        boolean enabled,
        @NotBlank String baseUrl,
        @Min(1) int lookbackDays,
        @Min(1) int maxComments,
        @Min(1) int maxTechnologies,
        @Min(1) int minMentions,
        @NotEmpty List<@NotBlank String> technologies,
        @NotEmpty List<@NotBlank String> priorityTechnologies
) {
}
