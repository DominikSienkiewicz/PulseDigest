package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CategoryPreferenceTest {

    @Test
    void noVotesLeaveTheScoreUntouched() {
        assertThat(CategoryPreference.multiplier(0)).isCloseTo(1.0, within(0.001));
    }

    @Test
    void eachNetVoteNudgesTheMultiplierByTwoPercent() {
        assertThat(CategoryPreference.multiplier(3)).isCloseTo(1.06, within(0.001));
        assertThat(CategoryPreference.multiplier(-3)).isCloseTo(0.94, within(0.001));
    }

    @Test
    void theMultiplierIsClampedSoNoAmountOfVotingCanBuryOrCrownACategory() {
        // The guard against the positive feedback loop: a muted category must stay reachable, or it
        // can never earn the votes that would revive it.
        assertThat(CategoryPreference.multiplier(1000)).isCloseTo(1.10, within(0.001));
        assertThat(CategoryPreference.multiplier(-1000)).isCloseTo(0.90, within(0.001));
    }

    @Test
    void aCategoryIsOnlyAnExpressedPreferenceOnceTheReaderHasVotedRepeatedly() {
        // One stray click is noise; it must not reach the prompt as a stated preference.
        assertThat(CategoryPreference.isExpressed(2)).isFalse();
        assertThat(CategoryPreference.isExpressed(-2)).isFalse();
        assertThat(CategoryPreference.isExpressed(3)).isTrue();
        assertThat(CategoryPreference.isExpressed(-3)).isTrue();
    }

    @Test
    void anUnknownCategoryFallsBackToNeutral() {
        assertThat(CategoryPreference.multiplierFor(null, java.util.Map.of("ai/llm", 5)))
                .isCloseTo(1.0, within(0.001));
        assertThat(CategoryPreference.multiplierFor("Missing", java.util.Map.of("ai/llm", 5)))
                .isCloseTo(1.0, within(0.001));
    }

    @Test
    void categoryLookupIsCaseInsensitive() {
        assertThat(CategoryPreference.multiplierFor("AI/LLM", java.util.Map.of("ai/llm", 3)))
                .isCloseTo(1.06, within(0.001));
    }
}
