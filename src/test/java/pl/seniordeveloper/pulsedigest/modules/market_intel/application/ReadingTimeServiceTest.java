package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReadingBudget;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** N04. An estimate that is allowed to be wrong, but not allowed to look precise. */
class ReadingTimeServiceTest {

    private final ReadingTimeService service = new ReadingTimeService();

    @Test
    void anEmptyEditionCostsNothingAndSaysSo() {
        assertThat(service.estimate(List.of())).isEqualTo(ReadingBudget.NOTHING);
        assertThat(service.estimate(null).isReportable()).isFalse();
    }

    @Test
    void aReleaseIsCheaperThanAPaper() {
        // The whole basis of the estimate: what kind of thing it is tells you more than its summary.
        int release = service.estimate(List.of(item("RELEASE", "short"))).minutes();
        int paper = service.estimate(List.of(item("RESEARCH", "short"))).minutes();

        assertThat(paper).isGreaterThan(release);
    }

    @Test
    void aLongSummaryNudgesTheEstimateWithinItsType() {
        int terse = service.estimate(List.of(item("NEWS", "krótko"))).minutes();
        int verbose = service.estimate(List.of(item("NEWS", "x".repeat(300)))).minutes();

        assertThat(verbose).isGreaterThanOrEqualTo(terse);
    }

    @Test
    void theTotalIsRoundedToFiveMinutesAndNeverToZero() {
        // "37 minut" claims this can tell 37 from 38. It cannot, and the reader would believe it.
        assertThat(service.estimate(List.of(item("PROMOTION", "x"))).minutes())
                .as("one one-minute item still reads as five, not as zero")
                .isEqualTo(5);
        assertThat(service.estimate(List.of(item("NEWS", "x"), item("NEWS", "x"), item("NEWS", "x")))
                .minutes() % 5).isZero();
    }

    @Test
    void anUnknownTypeFallsBackInsteadOfFailing() {
        assertThat(service.estimate(List.of(item("SOMETHING_NEW", "x"))).isReportable()).isTrue();
    }

    @Test
    void quickItemsAreTheOnesWorthTriagingBy() {
        assertThat(service.isQuick(item("RELEASE", "x"))).isTrue();
        assertThat(service.isQuick(item("RESEARCH", "x"))).isFalse();
    }

    private static pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem item(
            String type, String summary) {
        return TestModels.digestItem("T", "https://example.com/" + type + summary.length(), "S",
                "Java/JVM", type, 8, null, summary, "why");
    }
}
