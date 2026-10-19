package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * When an edition has too little above the bar to be worth a full digest (N03).
 *
 * <p>The product's stated thesis is signal over noise, and an edition that pads itself to fill its
 * own template contradicts it. Below {@code minItems} stories above the Must-know bar the mail
 * becomes three lines — "nothing this week deserves your attention, here is what came closest" —
 * instead of a document of leftovers. A digest willing to say nothing happened is more trustworthy
 * every other week, and that trust is the whole asset.
 *
 * <p>The threshold is configuration because this is a calibration, not a constant: too high and a
 * real week gets swallowed. Every run logs the count it measured and whether the note would fire,
 * so the decision accrues evidence whether or not it is enabled.
 */
@ConfigurationProperties(prefix = "report.thin-week")
@Validated
public record ThinWeekProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") @Min(1) int minItems
) {
}
