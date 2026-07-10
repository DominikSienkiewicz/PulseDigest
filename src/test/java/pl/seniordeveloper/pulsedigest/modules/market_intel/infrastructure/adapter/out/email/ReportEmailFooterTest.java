package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RadarAccuracy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
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
