package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sources the run must not even attempt (N27).
 *
 * <p>The consequence half of F7. Detection landed first: a source silent across three editions
 * already produces a WARN naming it. What was missing was anything to do about it — the digest kept
 * spending a fetch, a timeout budget and a prompt allocation on a source that returns nothing.
 *
 * <p>Disabling is a configured list rather than something the run decides for itself, and that is
 * deliberate. Configuration here is static, so a self-disabling source would have to write its own
 * configuration at runtime — and a source that switched itself off during an outage, with nothing
 * able to switch it back on, is a worse failure than the one being fixed. The run names the
 * candidate loudly in the log and in the digest footer; a human adds the line.
 *
 * @param disabled source names exactly as {@code MarketResearchService} labels them
 */
public record SourceDisablePolicy(Set<String> disabled) {

    public SourceDisablePolicy {
        disabled = disabled == null ? Set.of()
                : disabled.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(name -> name.strip().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** The three X/Twitter fetches, as {@code MarketResearchService} labels them (K7). */
    public static final Set<String> TWITTER_SOURCES =
            Set.of("Twitter/X influencer", "Twitter/X topic", "Twitter/X Anthropic");

    /** Whether any X source would be fetched this run — the question the credential check asks. */
    public boolean anyTwitterEnabled() {
        return TWITTER_SOURCES.stream().anyMatch(source -> !isDisabled(source));
    }

    /** Case-insensitively, because a name copied out of a log line should just work. */
    public boolean isDisabled(String sourceName) {
        return sourceName != null && disabled.contains(sourceName.strip().toLowerCase(Locale.ROOT));
    }
}
