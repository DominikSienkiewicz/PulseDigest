package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import java.time.Duration;

/**
 * Cooldown between consecutive report generations. {@code Duration.ZERO} disables the limit.
 */
public record RateLimitPolicy(Duration cooldown) {

    public boolean isEnabled() {
        return !cooldown.isZero() && !cooldown.isNegative();
    }
}
