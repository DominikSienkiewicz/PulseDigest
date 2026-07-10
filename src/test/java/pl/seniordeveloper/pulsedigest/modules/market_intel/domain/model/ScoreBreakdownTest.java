package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreBreakdownTest {

    private static ScoreBreakdown breakdown(int netSourceVotes, int netCategoryVotes, double categoryMultiplier) {
        return new ScoreBreakdown("Hacker News", 0.80, 0.80 + netSourceVotes * 0.05, netSourceVotes,
                12, categoryMultiplier, netCategoryVotes, 50);
    }

    @Test
    void theBaseScoreIsTheEffectiveWeightOnAHundredPointScale() {
        assertThat(breakdown(0, 0, 1.0).baseScore()).isEqualTo(80);
        assertThat(breakdown(4, 0, 1.0).baseScore()).isEqualTo(100);
    }

    @Test
    void aSourceWhoseWeightTheReaderMovedIsMarkedAsSuch() {
        assertThat(breakdown(-4, 0, 1.0).sourceWeightWasNudged()).isTrue();
        assertThat(breakdown(0, 0, 1.0).sourceWeightWasNudged()).isFalse();
    }

    @Test
    void aCategoryTheReaderExpressedAPreferenceOnIsMarkedAsSuch() {
        assertThat(breakdown(0, -5, 0.90).categoryWasNudged()).isTrue();
        assertThat(breakdown(0, 0, 1.0).categoryWasNudged()).isFalse();
    }

    @Test
    void aStoryConfirmedAcrossDomainsCarriesTheBonusThatSaysSo() {
        assertThat(breakdown(0, 0, 1.0).wasCrossSourceConfirmed()).isTrue();
        assertThat(new ScoreBreakdown("GitHub", 0.85, 0.85, 0, 0, 1.0, 0, 0)
                .wasCrossSourceConfirmed()).isFalse();
    }

    @Test
    void theReaderInfluencedTheScoreWhenEitherVoteDimensionMoved() {
        assertThat(breakdown(0, 0, 1.0).readerInfluenced()).isFalse();
        assertThat(breakdown(-2, 0, 1.0).readerInfluenced()).isTrue();
        assertThat(breakdown(0, 3, 1.06).readerInfluenced()).isTrue();
    }
}
