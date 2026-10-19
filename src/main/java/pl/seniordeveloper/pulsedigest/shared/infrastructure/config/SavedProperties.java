package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The "saved for later" shelf rendered from the mail's third action link (N05).
 *
 * <p>There is deliberately no window of its own here: saves are read over
 * {@code report.feedback.lookback-days}, the same window the rest of the feedback loop honours. Two
 * notions of "recent" in one loop would diverge, and the first symptom would be a reminder that
 * outlived the vote data explaining it.
 */
@ConfigurationProperties(prefix = "report.saved")
@Validated
public record SavedProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") @Min(1) int maxItems
) {
}
