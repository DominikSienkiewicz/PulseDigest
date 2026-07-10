package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Pre-scoring triage settings. A gpt-4o-mini pass rates the ~100 deterministically selected items by
 * title alone, and only the top {@code keep} reach gpt-4o — which reads the same payload at roughly
 * thirty times the price per token.
 *
 * <p>Disabled, or on any failure of the triage call, the full payload is sent through unchanged.
 */
@ConfigurationProperties(prefix = "report.pre-scoring")
@Validated
public record PreScoringProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("50") @Min(1) int keep
) {
}
