package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SourceWeightsTest {

    @Test
    void allKnownSourcesReturnExpectedWeights() {
        assertThat(SourceWeights.of("arXiv")).isCloseTo(0.70, within(0.001));
        assertThat(SourceWeights.of("GitHub Releases")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("Security Advisories")).isCloseTo(0.30, within(0.001));
        assertThat(SourceWeights.of("GitHub")).isCloseTo(0.85, within(0.001));
        assertThat(SourceWeights.of("Hacker News")).isCloseTo(0.80, within(0.001));
        assertThat(SourceWeights.of("Tech Radar")).isCloseTo(0.80, within(0.001));
        assertThat(SourceWeights.of("OpenJDK JEP")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.of("CNCF")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.of("Reddit")).isCloseTo(0.60, within(0.001));
        assertThat(SourceWeights.of("Product Hunt")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.of("YouTube")).isCloseTo(0.55, within(0.001));
        assertThat(SourceWeights.of("Hugging Face")).isCloseTo(0.70, within(0.001));
        assertThat(SourceWeights.of("RSS")).isCloseTo(0.45, within(0.001));
        assertThat(SourceWeights.of("Twitter/X")).isCloseTo(0.40, within(0.001));
        assertThat(SourceWeights.of("Bluesky")).isCloseTo(0.45, within(0.001));
        assertThat(SourceWeights.of("Mastodon")).isCloseTo(0.45, within(0.001));
    }

    @Test
    void githubReleasesExactMatchNotConfusedWithGithubPrefix() {
        assertThat(SourceWeights.of("GitHub Releases")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("GitHub")).isCloseTo(0.85, within(0.001));
    }

    @Test
    void longestPrefixWinsOnAmbiguousMatch() {
        // "GitHub Releases/spring-boot" matches "GitHub" (0.85) AND "GitHub Releases" (0.95)
        // Longest prefix must win → 0.95
        assertThat(SourceWeights.of("GitHub Releases/spring-boot")).isCloseTo(0.95, within(0.001));
    }

    @Test
    void cncfLandscapeSourceResolvesViaCncfPrefix() {
        // ReportPromptBuilder emits "CNCF Landscape"; SourceWeights key is "CNCF" — resolves via prefix
        assertThat(SourceWeights.of("CNCF Landscape")).isCloseTo(0.75, within(0.001));
    }

    @Test
    void labAnnouncementsCarryHighestCredibilityWeight() {
        // The README calls these the highest-signal source, yet every lab label used to fall through
        // to DEFAULT — which orphaned their 👎 votes and diluted cross-source correlation.
        assertThat(SourceWeights.of("Anthropic News")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("Anthropic Engineering")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("Claude Blog")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("Google Gemini Blog")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("OpenAI Dev Blog")).isCloseTo(0.95, within(0.001));
    }

    @Test
    void labLabelsCollapseToTheirLabKeyForFeedbackAggregation() {
        assertThat(SourceWeights.keyOf("Anthropic News")).isEqualTo("Anthropic");
        assertThat(SourceWeights.keyOf("Anthropic Engineering")).isEqualTo("Anthropic");
        assertThat(SourceWeights.keyOf("OpenAI Dev Blog")).isEqualTo("OpenAI");
    }

    @Test
    void openJdkJepIsNotSwallowedByTheOpenAiLabPrefix() {
        assertThat(SourceWeights.of("OpenJDK JEP")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.keyOf("OpenJDK JEP")).isEqualTo("OpenJDK JEP");
    }

    @Test
    void nullSourceReturnsDefault() {
        assertThat(SourceWeights.of(null)).isCloseTo(0.30, within(0.001));
    }

    @Test
    void unknownSourceReturnsDefault() {
        assertThat(SourceWeights.of("SomeNewSource")).isCloseTo(0.30, within(0.001));
    }

    @Test
    void blankSourceReturnsDefault() {
        assertThat(SourceWeights.of("")).isCloseTo(0.30, within(0.001));
        assertThat(SourceWeights.of("   ")).isCloseTo(0.30, within(0.001));
    }

    // --- C6 feedback nudging: of(source, netFeedbackVotes) ---

    @Test
    void zeroNetVotesEqualsBaseWeight() {
        assertThat(SourceWeights.of("Twitter/X", 0)).isCloseTo(0.40, within(0.001));
        assertThat(SourceWeights.of("Hacker News", 0)).isCloseTo(0.80, within(0.001));
    }

    @Test
    void netVotesNudgeWeightByZeroPointZeroFivePerVote() {
        // +4 → +0.20; −4 → −0.20 (step 0.05 per net vote)
        assertThat(SourceWeights.of("Twitter/X", 4)).isCloseTo(0.60, within(0.001));
        assertThat(SourceWeights.of("Twitter/X", -4)).isCloseTo(0.20, within(0.001));
    }

    @Test
    void deltaClampedToPlusMinusZeroPointThree() {
        // Reddit 0.60: ±100 votes → delta clamped at ±0.30 (both within [0.10, 0.99])
        assertThat(SourceWeights.of("Reddit", 100)).isCloseTo(0.90, within(0.001));
        assertThat(SourceWeights.of("Reddit", -100)).isCloseTo(0.30, within(0.001));
    }

    @Test
    void effectiveWeightFlooredAtZeroPointOne() {
        // Security Advisories 0.30 − 0.30 = 0.00 → floored to 0.10
        assertThat(SourceWeights.of("Security Advisories", -100)).isCloseTo(0.10, within(0.001));
    }

    @Test
    void effectiveWeightCappedBelowStrongThreshold() {
        // GitHub Releases 0.95 + 0.30 = 1.25 → capped at 0.99 so a nudge alone never reaches STRONG (base 100)
        assertThat(SourceWeights.of("GitHub Releases", 100)).isCloseTo(0.99, within(0.001));
    }

    // --- C6 feedback aggregation key: keyOf(source) ---

    @Test
    void keyOfCollapsesHighCardinalityLabelsToBaseSource() {
        assertThat(SourceWeights.keyOf("arXiv/cs.AI")).isEqualTo("arXiv");
        assertThat(SourceWeights.keyOf("Reddit/r/java")).isEqualTo("Reddit");
        assertThat(SourceWeights.keyOf("RSS/InfoQ Articles")).isEqualTo("RSS");
        assertThat(SourceWeights.keyOf("YouTube/Devoxx")).isEqualTo("YouTube");
    }

    @Test
    void keyOfPrefersExactThenLongestPrefix() {
        assertThat(SourceWeights.keyOf("Hacker News")).isEqualTo("Hacker News");
        assertThat(SourceWeights.keyOf("GitHub Releases")).isEqualTo("GitHub Releases");
        assertThat(SourceWeights.keyOf("GitHub Releases/spring-boot")).isEqualTo("GitHub Releases");
        assertThat(SourceWeights.keyOf("GitHub")).isEqualTo("GitHub");
    }

    @Test
    void keyOfUnknownSourceReturnsItself() {
        assertThat(SourceWeights.keyOf("Totally Unknown")).isEqualTo("Totally Unknown");
    }

    @Test
    void keyOfNullReturnsEmptyString() {
        assertThat(SourceWeights.keyOf(null)).isEqualTo("");
    }
}
