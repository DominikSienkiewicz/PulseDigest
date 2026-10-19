package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Configuration for the deterministic scorer (N30).
 *
 * <p>Defaults are the values that were compiled in until 2026-10-18, so an untouched configuration
 * scores exactly as before. See {@code ScoringPolicy} for why these particular numbers are the ones
 * worth moving out of the code.
 *
 * @param domainOverrides source-label prefix → {@code SourceDomain} name, e.g. {@code Reddit: CODE}
 */
@ConfigurationProperties(prefix = "report.scoring")
@Validated
public record ScoringProperties(
        @DefaultValue("3") @Min(1) int crossSourceThreshold,
        @DefaultValue("50") @Min(0) int crossSourceBonus,
        @DefaultValue("50") @Min(0) int maxEngagementBonus,
        @DefaultValue("1000") @Min(1) int engagementDivisor,
        @DefaultValue("100") @Min(1) int strongThreshold,
        @DefaultValue("60") @Min(1) int moderateThreshold,
        @DefaultValue Map<String, String> domainOverrides,
        @DefaultValue java.util.List<String> mutedTopics,
        @DefaultValue("0.5") @DecimalMin("0.1") @DecimalMax("1.0") double muteMultiplier
) {

    public ScoringProperties {
        domainOverrides = domainOverrides != null ? Map.copyOf(domainOverrides) : Map.of();
        mutedTopics = mutedTopics != null ? java.util.List.copyOf(mutedTopics) : java.util.List.of();
    }
}
