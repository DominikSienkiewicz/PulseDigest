package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalTest {

    private static final DigestItem ITEM = new DigestItem(
            "title", "https://example.com", "arXiv/cs.AI", "AI", "paper", 9, 5000, "summary");

    @Test
    void isCriticalTrendReturnsTrueForCriticalRank() {
        Signal signal = new Signal(ITEM, SignalRank.CRITICAL, 130, List.of(SourceDomain.SCIENCE));
        assertThat(signal.isCriticalTrend()).isTrue();
    }

    @Test
    void isCriticalTrendReturnsFalseForNonCriticalRank() {
        Signal signal = new Signal(ITEM, SignalRank.STRONG, 100, List.of(SourceDomain.CODE));
        assertThat(signal.isCriticalTrend()).isFalse();
    }

    @Test
    void sourceDomainsListIsImmutable() {
        List<SourceDomain> mutable = new ArrayList<>();
        mutable.add(SourceDomain.SCIENCE);
        Signal signal = new Signal(ITEM, SignalRank.MODERATE, 60, mutable);
        assertThatThrownBy(() -> signal.sourceDomains().add(SourceDomain.CODE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorRejectsNullItem() {
        assertThatThrownBy(() -> new Signal(null, SignalRank.WEAK, 30, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("item");
    }

    @Test
    void constructorRejectsNullRank() {
        assertThatThrownBy(() -> new Signal(ITEM, null, 30, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rank");
    }

    @Test
    void constructorRejectsNegativeSignalScore() {
        assertThatThrownBy(() -> new Signal(ITEM, SignalRank.WEAK, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signalScore");
    }
}
