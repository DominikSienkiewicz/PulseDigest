package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Conference calls for papers (N09).
 *
 * <p>Read from the community-maintained conference dataset behind confs.tech, which publishes one
 * JSON file per topic per year with {@code cfpEndDate} and {@code cfpUrl} as fields. Structured data
 * from one source beats scraping several: CFP pages are unstructured prose, and a deadline parsed
 * wrong is worse than a deadline missing.
 */
@ConfigurationProperties(prefix = "report.call-for-papers")
@Validated
public record CallForPapersProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://raw.githubusercontent.com/tech-conferences/conference-data/main/conferences")
        @NotBlank String baseUrl,
        @DefaultValue({"java", "javascript", "devops", "python", "ai"}) List<String> topics,
        @DefaultValue("120") @Min(1) int lookaheadDays,
        @DefaultValue("5") @Min(1) int limit
) {

    public CallForPapersProperties {
        topics = topics != null ? List.copyOf(topics) : List.of();
    }
}
