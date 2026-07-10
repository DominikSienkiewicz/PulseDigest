package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Report-history settings. Past editions are read back from the {@code reports} table to give the
 * digest a memory: how many consecutive editions a story has held, when the reader first saw it,
 * and what the week added up to.
 *
 * <p>The default window spans three weeks — nine editions at the Mon/Wed/Fri cadence — which is long
 * enough for a trend to establish itself and short enough that a resolved story stops being echoed.
 */
@ConfigurationProperties(prefix = "report.history")
@Validated
public record ReportHistoryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("21") @Min(1) int lookbackDays
) {
}
