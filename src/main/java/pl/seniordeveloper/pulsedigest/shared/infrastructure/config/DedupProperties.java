package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Cross-edition deduplication settings. When enabled, items whose canonical URL already appeared in
 * an edition within {@code lookbackDays} are dropped before LLM scoring — so the wider lookback
 * windows of the Mon/Thu schedule do not re-surface the same item across consecutive runs.
 */
@ConfigurationProperties(prefix = "report.dedup")
@Validated
public record DedupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") @Min(1) int lookbackDays
) {
}
