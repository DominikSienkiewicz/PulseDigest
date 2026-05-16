package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "report.research")
@Validated
public record ResearchProperties(
        @Min(0) int minLikes,
        @Min(1) int daysBack,
        @NotEmpty List<@NotBlank String> authorityUsernames
) {
}
