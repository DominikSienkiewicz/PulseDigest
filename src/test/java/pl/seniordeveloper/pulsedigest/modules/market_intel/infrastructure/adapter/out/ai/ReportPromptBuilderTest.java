package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPromptBuilderTest {

    @Test
    void preScoreHighWeightSourceBeatsLowWeightHighEngagement() {
        // arXiv eng=0: round(1.00×100)+0 = 100
        // Twitter/X eng=999: round(0.40×100)+0 = 40
        assertThat(ReportPromptBuilder.preScore("arXiv/cs.AI", 0))
                .isGreaterThan(ReportPromptBuilder.preScore("Twitter/X", 999));
    }

    @Test
    void preScoreEngagementBonusCappedAt50() {
        // Twitter/X: round(0.40×100) + min(50, 999_999/1_000) = 40 + 50 = 90
        assertThat(ReportPromptBuilder.preScore("Twitter/X", 999_999)).isEqualTo(90);
    }

    @Test
    void preScoreArxivWithMaxEngagementReaches150() {
        // arXiv: 100 + 50 = 150
        assertThat(ReportPromptBuilder.preScore("arXiv/cs.AI", 999_999)).isEqualTo(150);
    }

    @Test
    void applyTotalCapKeepsHighWeightItemOverLowWeightHighEngagement() {
        // 100 Twitter items (preScore=40) + 1 arXiv item (preScore=100)
        // arXiv must survive despite zero engagement
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(Map.of("source", "Twitter/X", "engagement_score", 999));
        }
        items.add(Map.of("source", "arXiv/cs.AI", "engagement_score", 0));

        List<Map<String, Object>> result = ReportPromptBuilder.applyTotalCap(items, 100);

        assertThat(result).hasSize(100);
        assertThat(result.stream().anyMatch(m -> "arXiv/cs.AI".equals(m.get("source"))))
                .as("arXiv item must survive the cap despite zero engagement")
                .isTrue();
        assertThat(result.get(0).get("source"))
                .as("arXiv must be first — highest preScore")
                .isEqualTo("arXiv/cs.AI");
    }

    @Test
    void applyTotalCapIsNoopWhenUnderCap() {
        List<Map<String, Object>> items = List.of(
                Map.of("source", "GitHub", "engagement_score", 100),
                Map.of("source", "arXiv/cs.AI", "engagement_score", 0)
        );
        List<Map<String, Object>> result = ReportPromptBuilder.applyTotalCap(items, 100);
        assertThat(result).hasSize(2);
        assertThat(result).isSameAs(items);
    }
}
