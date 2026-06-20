package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SourceWeightsTest {

    @Test
    void allKnownSourcesReturnExpectedWeights() {
        assertThat(SourceWeights.of("arXiv")).isCloseTo(1.00, within(0.001));
        assertThat(SourceWeights.of("GitHub Releases")).isCloseTo(0.95, within(0.001));
        assertThat(SourceWeights.of("Security Advisories")).isCloseTo(0.30, within(0.001));
        assertThat(SourceWeights.of("GitHub")).isCloseTo(0.85, within(0.001));
        assertThat(SourceWeights.of("Hacker News")).isCloseTo(0.80, within(0.001));
        assertThat(SourceWeights.of("Tech Radar")).isCloseTo(0.80, within(0.001));
        assertThat(SourceWeights.of("OpenJDK JEP")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.of("CNCF")).isCloseTo(0.75, within(0.001));
        assertThat(SourceWeights.of("Reddit")).isCloseTo(0.60, within(0.001));
        assertThat(SourceWeights.of("Product Hunt")).isCloseTo(0.55, within(0.001));
        assertThat(SourceWeights.of("YouTube")).isCloseTo(0.55, within(0.001));
        assertThat(SourceWeights.of("Hugging Face")).isCloseTo(0.50, within(0.001));
        assertThat(SourceWeights.of("RSS")).isCloseTo(0.45, within(0.001));
        assertThat(SourceWeights.of("Twitter/X")).isCloseTo(0.40, within(0.001));
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
}
