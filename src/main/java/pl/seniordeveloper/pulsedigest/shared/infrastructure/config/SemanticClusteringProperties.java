package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Collapsing the same story told under several headlines (N21).
 *
 * <p>Enabled but <strong>not</strong> enforcing by default. Over-merging is worse than duplication:
 * a duplicate costs the reader a line, while a wrong merge manufactures a corroboration — and since
 * F2 corroboration is the only route to 🔴, so it manufactures a critical trend out of two unrelated
 * articles. The shadow log is what the threshold should be tuned against, using {@code --replay}.
 */
@ConfigurationProperties(prefix = "report.semantic-clustering")
@Validated
public record SemanticClusteringProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean enforcing,
        @DefaultValue("0.86") @DecimalMin("0.5") @DecimalMax("1.0") double threshold,
        @DefaultValue("4") @Min(2) int maxClusterSize
) {
}
