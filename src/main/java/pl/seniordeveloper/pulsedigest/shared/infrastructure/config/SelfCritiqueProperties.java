package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The second opinion on each edition (N23).
 *
 * <p>Ships enabled but <strong>not</strong> enforcing, and that default is the feature: the verdict
 * is logged for a few editions and read before it is given authority over what the reader sees.
 */
@ConfigurationProperties(prefix = "report.self-critique")
@Validated
public record SelfCritiqueProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean enforcing
) {
}
