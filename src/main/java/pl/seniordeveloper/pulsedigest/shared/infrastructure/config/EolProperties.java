package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;

/**
 * The end-of-life table (N08).
 *
 * <p>Hand-kept on purpose rather than read from an API: one more HTTP call means one more source
 * that can fail, and these dates change a handful of times a year. The price of keeping it by hand
 * is that it rots, so {@code validAsOf} is mandatory and rendered — a table that cannot show its own
 * age is the trap {@code market-knowledge-2026.txt} was in before it was deleted (N01).
 *
 * @param validAsOf when the dates below were last verified against the upstream sources
 */
@ConfigurationProperties(prefix = "report.eol")
@Validated
public record EolProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("6") @Min(1) int warnWithinMonths,
        @NotNull LocalDate validAsOf,
        @Valid @DefaultValue List<Entry> entries
) {

    public EolProperties {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public record Entry(@NotBlank String name, @NotNull LocalDate date) {
    }
}
