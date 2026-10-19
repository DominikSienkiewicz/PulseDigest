package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The application-level double-send guard (F16).
 *
 * <p>{@code claimTimeoutMinutes} is how long an unfinished claim is respected before another run may
 * take it over. It exists for the run that dies between claiming and sending: without a takeover
 * window that edition could never be retried, which would make the guard worse than the problem.
 * Set it longer than a send can take and shorter than the gap between editions — the digest
 * workflow's own {@code timeout-minutes} is the number to look at.
 */
@ConfigurationProperties(prefix = "report.delivery")
@Validated
public record DeliveryProperties(
        @DefaultValue("true") boolean guardEnabled,
        @DefaultValue("60") @Min(1) int claimTimeoutMinutes
) {
}
