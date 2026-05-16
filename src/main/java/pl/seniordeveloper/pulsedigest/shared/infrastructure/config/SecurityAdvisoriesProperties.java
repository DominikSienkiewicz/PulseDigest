package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.security-advisories")
@Validated
public record SecurityAdvisoriesProperties(
        @NotBlank String baseUrl,
        @Min(1) int limit,
        @Min(1) int lookbackHours,
        @NotEmpty List<@NotBlank String> minSeverities,
        @NotEmpty List<@NotBlank String> relevantEcosystems
) {
}
