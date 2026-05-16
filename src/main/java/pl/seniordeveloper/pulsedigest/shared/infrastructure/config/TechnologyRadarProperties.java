package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report.technology-radar")
@Validated
public record TechnologyRadarProperties(
        @NotBlank String baseUrl,
        @NotBlank String dataPath,
        @Min(1) int lookbackMonths
) {
}
