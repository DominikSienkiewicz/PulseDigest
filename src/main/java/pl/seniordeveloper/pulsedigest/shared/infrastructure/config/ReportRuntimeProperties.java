package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Root-level report runtime knobs that do not belong to any single source.
 */
@ConfigurationProperties(prefix = "report")
@Validated
public record ReportRuntimeProperties(
        @Min(0) int minGenerationIntervalMinutes
) {
}
