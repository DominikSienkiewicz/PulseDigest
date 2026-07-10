package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Living reader-model settings. The persona in {@code interest-profile.persona} is a frozen string;
 * this is the part that learns. Every knob here is a guard against profile drift — see
 * {@code ReaderProfilePolicy}.
 */
@ConfigurationProperties(prefix = "report.reader-profile")
@Validated
public record ReaderProfileProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") @Min(1) int minVotes,
        @DefaultValue("7") @Min(1) int refreshDays,
        @DefaultValue("60") @Min(1) int hypothesisTtlDays
) {
}
