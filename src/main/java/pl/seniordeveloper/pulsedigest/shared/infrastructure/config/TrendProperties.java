package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report.trend")
@Validated
public record TrendProperties(
        boolean enabled,
        @Min(1) int lookbackDays,
        @Min(1) int minOccurrences,
        @Min(1) int maxClusters
) {
}
