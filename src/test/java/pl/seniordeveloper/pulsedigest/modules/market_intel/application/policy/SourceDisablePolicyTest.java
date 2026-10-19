package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** N27. The consequence half of F7 — detection already existed, this is what acts on it. */
class SourceDisablePolicyTest {

    @Test
    void matchesTheNameExactlyAsTheRunLogPrintsIt() {
        // The name is copied out of a WARN line; requiring the reader to get the case right as well
        // would make the one action the log recommends fail silently.
        var policy = new SourceDisablePolicy(Set.of("Tech Radar"));

        assertThat(policy.isDisabled("Tech Radar")).isTrue();
        assertThat(policy.isDisabled("tech radar")).isTrue();
        assertThat(policy.isDisabled("  TECH RADAR  ")).isTrue();
        assertThat(policy.isDisabled("Hacker News")).isFalse();
    }

    @Test
    void anEmptyListDisablesNothing() {
        assertThat(new SourceDisablePolicy(Set.of()).isDisabled("Hacker News")).isFalse();
        assertThat(new SourceDisablePolicy(null).isDisabled("Hacker News")).isFalse();
    }

    @Test
    void blankEntriesAreIgnoredRatherThanDisablingAnUnnamedSource() {
        var policy = new SourceDisablePolicy(Set.of("  ", "Tech Radar"));

        assertThat(policy.disabled()).containsExactly("tech radar");
        assertThat(policy.isDisabled("")).isFalse();
        assertThat(policy.isDisabled(null)).isFalse();
    }
}
