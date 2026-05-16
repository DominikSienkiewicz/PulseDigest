package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.libraries-io")
@Validated
public record LibrariesIoProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @Min(1) int limit,
        @NotEmpty List<@NotBlank String> platforms,
        @Min(1) int lookbackDays
) {
}
