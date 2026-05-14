package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@ConfigurationProperties(prefix = "twitter")
@Validated
public record TwitterProperties(
        @NotBlank
        String bearerToken,
        @NotEmpty
        List<@NotBlank String> queries,
        @NotEmpty
        List<@NotBlank String> accounts
) {
}
