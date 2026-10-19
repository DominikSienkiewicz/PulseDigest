package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K7, settled 2026-10-19: X/Twitter stays in the code and is switched off.
 *
 * <p>The decision only means something if "off" actually costs nothing — and the thing that would
 * have made it cost something is the credential. {@code twitter.bearer-token} was {@code @NotBlank},
 * so a run with X disabled would still have failed at startup for want of the one paid credential in
 * the project, which is the opposite of what disabling it is for.
 */
class TwitterDisabledTest {

    @Test
    void theShippedConfigurationDisablesAllThreeXFetches() {
        // All three, not one: the sources are fetched separately and a stray enabled one would
        // quietly put the paid credential back on the critical path.
        var policy = new SourceDisablePolicy(SourceDisablePolicy.TWITTER_SOURCES);

        assertThat(policy.anyTwitterEnabled()).isFalse();
        assertThat(SourceDisablePolicy.TWITTER_SOURCES).hasSize(3);
        SourceDisablePolicy.TWITTER_SOURCES.forEach(source ->
                assertThat(policy.isDisabled(source)).as("%s is disabled", source).isTrue());
    }

    @Test
    void leavingOneEnabledStillCountsAsEnabled() {
        var policy = new SourceDisablePolicy(Set.of("Twitter/X topic", "Twitter/X Anthropic"));

        assertThat(policy.anyTwitterEnabled())
                .as("Twitter/X influencer is still on, so the credential is still needed")
                .isTrue();
    }

    @Test
    void disablingOtherSourcesSaysNothingAboutX() {
        assertThat(new SourceDisablePolicy(Set.of("Tech Radar")).anyTwitterEnabled()).isTrue();
    }
}
