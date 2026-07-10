package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Reader feedback loop (C6) settings. The headless batch only READS feedback; an external receiver
 * writes rows from the email's 👍/👎 links. When {@code enabled}, down-voted item URLs from the last
 * {@code lookbackDays} are suppressed before LLM scoring. {@code receiverUrl} is the external endpoint
 * the email links point at — when blank, no feedback links are rendered (graceful degradation).
 *
 * <p>{@code signingSecret} is shared with that receiver. When set, every 👍/👎 link carries an
 * {@code &sig=} HMAC over {@code url|vote|source|edition}, so a vote cannot be forged or edited in
 * flight. When blank, links are rendered unsigned and today's receiver keeps working unchanged —
 * which is what lets the signature be rolled out before the receiver knows how to verify it.
 */
@ConfigurationProperties(prefix = "report.feedback")
@Validated
public record FeedbackProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30") @Min(1) int lookbackDays,
        @DefaultValue("") String receiverUrl,
        @DefaultValue("") String signingSecret
) {

    /** Whether outgoing feedback links should carry an HMAC signature. */
    public boolean signingEnabled() {
        return signingSecret != null && !signingSecret.isBlank();
    }
}
