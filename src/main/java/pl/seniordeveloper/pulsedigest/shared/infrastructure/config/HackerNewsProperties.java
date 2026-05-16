package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.hacker-news")
@Validated
public record HackerNewsProperties(
        @NotBlank String baseUrl,
        @NotEmpty List<@NotBlank String> keywords,
        @Min(1) int limit,
        @Min(0) int minScore
) {
}
