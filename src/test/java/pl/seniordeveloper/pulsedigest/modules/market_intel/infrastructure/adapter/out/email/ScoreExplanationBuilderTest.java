package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreExplanationBuilderTest {

    private static Signal signal(String source, ScoreBreakdown breakdown) {
        DigestItem item = new DigestItem("Title", "https://example.com/x", source, "AI/LLM",
                "RELEASE", 8, 10, "Summary", null);
        return new Signal(item, SignalRank.STRONG, 100, List.of(SourceDomain.CODE), null, null, breakdown);
    }

    private static ScoreBreakdown breakdown(int netSourceVotes, int netCategoryVotes,
                                            double multiplier, int crossSourceBonus) {
        return new ScoreBreakdown("Hacker News", 0.80, 0.80 + netSourceVotes * 0.05, netSourceVotes,
                12, multiplier, netCategoryVotes, crossSourceBonus);
    }

    // --- "Dlaczego to widzisz" ---

    @Test
    void namesTheCredibilityAndEngagementThatEarnedTheScore() {
        String html = ScoreExplanationBuilder.buildWhyYouSeeThis(signal("Hacker News", breakdown(0, 0, 1.0, 0)));

        assertThat(html).contains("Dlaczego to widzisz");
        assertThat(html).contains("Hacker News").contains("80").contains("+12");
    }

    @Test
    void namesTheCrossSourceConfirmationWhenItWasEarned() {
        String html = ScoreExplanationBuilder.buildWhyYouSeeThis(signal("Hacker News", breakdown(0, 0, 1.0, 50)));

        assertThat(html).contains("potwierdzenie").contains("+50");
    }

    @Test
    void saysNothingAboutCrossSourceWhenTheStoryStoodAlone() {
        String html = ScoreExplanationBuilder.buildWhyYouSeeThis(signal("Hacker News", breakdown(0, 0, 1.0, 0)));

        assertThat(html).doesNotContain("+50");
    }

    @Test
    void showsTheReaderWhatHisOwnVotesDidToThisItem() {
        String html = ScoreExplanationBuilder.buildWhyYouSeeThis(signal("Hacker News", breakdown(-4, -5, 0.90, 0)));

        assertThat(html).contains("Twoje głosy");
    }

    @Test
    void isEmptyForASignalScoredBeforeBreakdownsExisted() {
        // Legacy editions read back from JSONB carry no breakdown; the mail must not invent one.
        assertThat(ScoreExplanationBuilder.buildWhyYouSeeThis(signal("Hacker News", null))).isEmpty();
        assertThat(ScoreExplanationBuilder.buildWhyYouSeeThis(null)).isEmpty();
    }

    // --- "Twoje głosy w akcji" (footer) ---

    @Test
    void footerReportsEverySourceWhoseWeightTheReaderMoved() {
        String html = ScoreExplanationBuilder.buildVotesInAction(List.of(
                signal("Hacker News", breakdown(-4, 0, 1.0, 0)),
                signal("Hacker News", breakdown(-4, 0, 1.0, 0))));

        assertThat(html).contains("Twoje głosy w akcji");
        assertThat(html).contains("Hacker News");
        assertThat(html).contains("0.80").contains("0.60");
    }

    @Test
    void footerReportsEachSourceOnceEvenWhenSeveralItemsCarryIt() {
        String html = ScoreExplanationBuilder.buildVotesInAction(List.of(
                signal("Hacker News", breakdown(-4, 0, 1.0, 0)),
                signal("Hacker News", breakdown(-4, 0, 1.0, 0))));

        assertThat(html.split("Hacker News", -1).length - 1).isEqualTo(1);
    }

    @Test
    void footerIsSilentWhileNoVoteHasMovedAnything() {
        assertThat(ScoreExplanationBuilder.buildVotesInAction(
                List.of(signal("Hacker News", breakdown(0, 0, 1.0, 0))))).isEmpty();
        assertThat(ScoreExplanationBuilder.buildVotesInAction(List.of())).isEmpty();
    }
}
