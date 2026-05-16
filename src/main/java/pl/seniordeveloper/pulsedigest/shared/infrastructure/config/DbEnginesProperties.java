package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report.db-engines")
@Validated
public record DbEnginesProperties(
        @NotBlank String baseUrl,
        @Min(1) int lookbackDays,
        @Min(0) int minScoreChange
) {
}
