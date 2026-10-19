package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.testing.TestModels;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N29. The explanation is only worth printing if the reader can check it: the components shown have
 * to add up to the total shown, and the total has to be the score the digest actually ranked by.
 */
class ScoreArithmeticTest {

    private final SignalScoringService scorer = new SignalScoringService(ScoringPolicy.defaults());

    @Test
    void theComponentsShownAddUpToTheTotalShown() {
        // arXiv weight 0.70 → base 70, engagement 12_000 → +12, one domain, no vote nudges.
        Signal signal = scorer.score(List.of(item("arXiv/cs.AI", "Research", 12_000))).getFirst();

        assertThat(signal.breakdown().baseScore() + signal.breakdown().engagementBonus()
                + signal.breakdown().crossSourceBonus())
                .as("the parts the mail prints must reconstruct the score it ranked by")
                .isEqualTo(signal.signalScore());

        assertThat(ScoreExplanationBuilder.buildWhyYouSeeThis(signal))
                .contains("arXiv")
                .contains("70")
                .contains("engagement +12")
                .contains("= <strong>82 pkt</strong>");
    }

    @Test
    void theCompactFormInTheTablesCarriesTheSameTotal() {
        Signal signal = scorer.score(List.of(item("Hacker News", "Java", 3_000))).getFirst();

        assertThat(ScoreExplanationBuilder.buildCompactArithmetic(signal))
                .contains("Hacker News")
                .contains("= " + signal.signalScore() + " pkt");
    }

    /** A corroborated story has to show the bonus that made it 🔴, or the badge looks arbitrary. */
    @Test
    void aCorroboratedStoryShowsTheBonusThatEarnedItsBadge() {
        List<Signal> confirmed = scorer.score(List.of(topicItem("arXiv/cs.AI", "Research", "mcp"),
                topicItem("GitHub", "Open Source", "mcp"),
                topicItem("Hacker News", "Community", "mcp")));

        assertThat(ScoreExplanationBuilder.buildWhyYouSeeThis(confirmed.getFirst()))
                .contains("potwierdzenie w 3+ domenach +50");
    }

    /** An edition stored before breakdowns existed must render nothing rather than a row of zeros. */
    @Test
    void anUnscoredSignalExplainsNothing() {
        assertThat(ScoreExplanationBuilder.buildWhyYouSeeThis(null)).isEmpty();
        assertThat(ScoreExplanationBuilder.buildCompactArithmetic(null)).isEmpty();
    }

    private static DigestItem item(String source, String category, int engagement) {
        return TestModels.digestItem("title", "http://example.com/" + Math.abs(source.hashCode()),
                source, category, "TYPE", 5, engagement, "summary", null);
    }

    private static DigestItem topicItem(String source, String category, String topicKey) {
        return new DigestItem("title", "http://example.com/" + Math.abs((source + topicKey).hashCode()),
                source, category, "TYPE", 5, 0, "summary", null, topicKey);
    }
}
