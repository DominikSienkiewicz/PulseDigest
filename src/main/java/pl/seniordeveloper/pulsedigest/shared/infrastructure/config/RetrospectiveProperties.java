package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The quarterly retrospective (N20), which runs on demand rather than on a schedule:
 * {@code java -jar app.jar --retrospective}.
 *
 * <p>No {@code enabled} flag, deliberately. The feature only happens when somebody asks for it, so
 * a switch would be a second way of saying the same thing.
 */
@ConfigurationProperties(prefix = "report.retrospective")
@Validated
public record RetrospectiveProperties(
        @DefaultValue("90") @Min(1) int lookbackDays,
        @DefaultValue("30") @Min(1) int maturityDays
) {
}
