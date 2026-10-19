package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where to read the Thoughtworks Technology Radar from.
 *
 * <p>Carried a {@code lookbackMonths} until 2026-10-18 — declared, {@code @Min(1)}-validated,
 * documented, and read by no code at all (N57). The 2026-10-15 audit blamed it for silently
 * filtering the source empty; in fact the adapter never looked at it and there was no date
 * filtering anywhere, which a live check settled: the URL returns 118 entries at HTTP 200.
 */
@ConfigurationProperties(prefix = "report.technology-radar")
@Validated
public record TechnologyRadarProperties(
        @NotBlank String baseUrl,
        @NotBlank String dataPath
) {
}
