package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.cncf-landscape")
@Validated
public record CncfLandscapeProperties(
        @NotBlank String baseUrl,
        @Min(1) int lookbackDays,
        @NotEmpty List<@NotBlank String> relevantStatuses
) {
}
