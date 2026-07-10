package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The footer is where the digest is accountable for itself: the radar's own hit rate, and the reader
 * model that shaped what was selected. Both must be visible, and both must be absent before they have
 * anything true to say.
 */
class ReportEmailFooterTest {

    private final ReportEmailBuilder builder = new ReportEmailBuilder(
            new FeedbackProperties(false, 30, "", ""), new WatchlistProperties(false, List.of()));

    private static ReportData report() {
        DigestItem item = new DigestItem("Item", "https://example.com/item", "GitHub", "Java",
                "RELEASE", 8, 10, "Summary", null);
        return new ReportData("Preview", "Lead", List.of("Insight"), List.of(item), List.of());
    }

    @Test
    void publishesTheRadarsOwnHitRate() {
        assertThat(builder.buildHtml(report().withRadarAccuracy(new RadarAccuracy(10, 7)), null))
                .contains("radar: 7/10 kandydatów osiągnęło CRITICAL (70%)");
    }

    @Test
    void omitsTheRadarLineUntilAPredictionHasBeenJudged() {
        assertThat(builder.buildHtml(report().withRadarAccuracy(new RadarAccuracy(0, 0)), null))
                .doesNotContain("radar:");
    }

    @Test
    void disclosesTheReaderModelThatShapedTheDigest() {
        // A model distilled from the reader's clicks, silently reshaping what he sees, must be
        // auditable — with the evidence behind every claim.
        ReportData withProfile = report().withReaderProfile(new ReaderProfile(
                Instant.parse("2026-07-06T06:00:00Z"), 20,
                List.of(new ProfileHypothesis("Chce więcej Javy", "+8 netto w java/jvm",
                        LocalDate.of(2026, 7, 6)))));

        String html = builder.buildHtml(withProfile, null);

        assertThat(html).contains("Model czytelnika");
        assertThat(html).contains("Chce więcej Javy").contains("+8 netto w java/jvm");
    }

    @Test
    void footerShowsWhatTheReadersVotesDidToTheSourceWeights() {
        // Investment → reward: a 👎 whose effect is invisible is a 👎 nobody casts twice.
        DigestItem item = new DigestItem("Item", "https://example.com/item", "Hacker News", "Java",
                "RELEASE", 8, 10, "Summary", null);
        Signal nudged = new Signal(item, SignalRank.STRONG, 90, List.of(SourceDomain.BUSINESS), null, null,
                new ScoreBreakdown("Hacker News", 0.80, 0.60, -4, 0, 1.0, 0, 0));
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(item), List.of(nudged));

        String html = builder.buildHtml(report, null);

        assertThat(html).contains("Twoje głosy w akcji");
        assertThat(html).contains("Hacker News").contains("0.80").contains("0.60");
    }

    @Test
    void footerSaysNothingAboutVotesThatMovedNothing() {
        assertThat(builder.buildHtml(report(), null)).doesNotContain("Twoje głosy w akcji");
    }

    @Test
    void theMicroLineExplainsMustKnowItemsButNotTheLongTable() {
        // The risk this feature carries is visual noise. The explanation belongs where the reader
        // reads closely, not under every row of a twenty-line table.
        DigestItem mustKnow = new DigestItem("Hero", "https://example.com/hero", "Hacker News", "Java",
                "RELEASE", 9, 10, "Summary", "Do this");
        DigestItem midTier = new DigestItem("Mid", "https://example.com/mid", "Reddit/r/java", "Java",
                "OPINION", 6, 10, "Summary", null);
        ScoreBreakdown b = new ScoreBreakdown("Hacker News", 0.80, 0.80, 0, 12, 1.0, 0, 0);
        ReportData report = new ReportData("Preview", "Lead", List.of(), List.of(mustKnow, midTier),
                List.of(new Signal(mustKnow, SignalRank.STRONG, 92, List.of(SourceDomain.BUSINESS), null, null, b),
                        new Signal(midTier, SignalRank.MODERATE, 60, List.of(SourceDomain.SOCIAL), null, null, b)));

        String html = builder.buildHtml(report, null);

        assertThat(html.split("Dlaczego to widzisz", -1).length - 1)
                .as("rendered once, on the Must-know hero item only")
                .isEqualTo(1);
    }

    @Test
    void saysNothingAboutAReaderModelThatDoesNotExistYet() {
        assertThat(builder.buildHtml(report(), null)).doesNotContain("Model czytelnika");
    }

    @Test
    void escapesHypothesisTextSoAModelCannotInjectMarkupIntoTheMail() {
        ReportData withProfile = report().withReaderProfile(new ReaderProfile(
                Instant.parse("2026-07-06T06:00:00Z"), 20,
                List.of(new ProfileHypothesis("<script>x</script>", "<b>evidence</b>",
                        LocalDate.of(2026, 7, 6)))));

        assertThat(builder.buildHtml(withProfile, null))
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }
}
