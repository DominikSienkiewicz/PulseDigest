package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report.arxiv")
@Validated
public record ArxivProperties(
        @NotBlank String categories,
        @NotBlank String keywords,
        @Min(1) int maxResults,
        @Min(1) int lookbackHours
) {
}
