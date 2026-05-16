package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.product-hunt")
@Validated
public record ProductHuntProperties(
        @NotBlank String baseUrl,
        String developerToken,
        @Min(0) int minVotes,
        @Min(1) int lookbackHours,
        @NotEmpty List<@NotBlank String> relevantTopics
) {
}
