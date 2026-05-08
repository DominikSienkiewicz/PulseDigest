package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalRankTest {

    @Test
    void displayPriorityOrderIsCriticalStrongModerateWeak() {
        assertThat(SignalRank.values())
                .containsExactly(SignalRank.CRITICAL, SignalRank.STRONG, SignalRank.MODERATE, SignalRank.WEAK);
    }
}
