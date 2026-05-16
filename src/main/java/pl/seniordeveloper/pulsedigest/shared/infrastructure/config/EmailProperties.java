package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report.email")
@Validated
public record EmailProperties(
        @NotBlank String resendApiKey,
        @NotBlank String from,
        @NotBlank String to
) {
}
