package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionOutcome;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PredictionScoreboard;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Retrospective;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceYield;
import pl.seniordeveloper.pulsedigest.testing.TestModels;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quarterly retrospective's rendering (N20), including the graded 🔴 badge (N11).
 *
 * <p>This class shipped with no test at all, and the build did not object: the JaCoCo gate counts
 * instructions, and 87% of them were covered elsewhere, so ninety uncovered lines and twenty
 * uncovered branches in one builder passed unnoticed. Sonar's coverage blends line and condition
 * coverage, which is why it saw what the gate did not — the number that prompted this test.
 *
 * <p>What is worth pinning here is not the markup. It is the three judgements the renderer makes:
 * that a rate nobody should trust is refused rather than printed, that a call which held up reads
 * differently from one that did not, and that a hostile title or URL cannot leave the page as markup
 * or as a live link.
 */
class RetrospectiveEmailBuilderTest {

    private static final LocalDate FROM = LocalDate.parse("2026-07-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-30");

    private final RetrospectiveEmailBuilder builder = new RetrospectiveEmailBuilder();

    @Test
    void theSubjectNamesTheQuarterInTheReadersDateFormat() {
        assertThat(builder.buildSubject(retrospective(scoreboard(), List.of(), List.of(), List.of())))
                .isEqualTo("📊 PulseDigest — kwartał 01.07.2026 – 30.09.2026");
    }

    @Test
    void aRateBelowThreeGradedCallsIsRefusedRatherThanPrinted() {
        // The scoreboard's own floor: with two calls one story swings the rate by half, and a number
        // that moves that far on one data point claims precision it does not have. The section still
        // renders — a scoreboard that only appears on a good quarter is an advertisement.
        PredictionScoreboard thin = scoreboard(outcome("Spring Boot 4.1", 2, false),
                outcome("Virtual threads", 1, false));

        String html = builder.buildHtml(retrospective(thin, List.of(), List.of(), List.of()));

        assertThat(html)
                .contains("Co się stało z krytycznymi trendami")
                .contains("Za mało dojrzałych zgłoszeń")
                .contains("(2 z wymaganych 3)")
                .doesNotContain("obroniło się");
    }

    @Test
    void aReportableQuarterStatesTheRateAndTheMaturityWindowItWasMeasuredOver() {
        PredictionScoreboard board = scoreboard(
                outcome("Held by later editions", 3, false),
                outcome("Upvoted by the reader", 0, true),
                outcome("Faded away", 0, false));

        String html = builder.buildHtml(retrospective(board, List.of(), List.of(), List.of()));

        assertThat(html)
                .as("two of three held up — the rate, the denominator and the maturity rule all stated")
                .contains("<strong>2 z 3</strong>")
                .contains("67%")
                .contains("niż 14 dni");
    }

    @Test
    void aCallThatHeldUpReadsDifferentlyFromOneThatDidNot() {
        PredictionScoreboard board = scoreboard(
                outcome("Both kinds of evidence", 4, true),
                outcome("Only later appearances", 2, false),
                outcome("Only the reader", 0, true),
                outcome("Nothing at all", 0, false));

        String html = builder.buildHtml(retrospective(board, List.of(), List.of(), List.of()));

        assertThat(html)
                .contains("trzymało się i zostało docenione")
                .contains("wracało w kolejnych wydaniach")
                .contains("doceniona przez Ciebie")
                .contains("zgasło bez śladu")
                .as("the verdict is also carried by colour, not by wording alone")
                .contains("#15803d")
                .contains("#6b7280");
    }

    @Test
    void theThreeOptionalSectionsAreAbsentWhenThereIsNothingToPutInThem() {
        String html = builder.buildHtml(retrospective(scoreboard(), List.of(), List.of(), List.of()));

        assertThat(html)
                .doesNotContain("Co doceniłeś")
                .doesNotContain("Tematy, które nie zniknęły")
                .doesNotContain("Co wniosły źródła");
    }

    @Test
    void eachOptionalSectionRendersItsOwnContentWhenItHasAny() {
        Retrospective full = retrospective(scoreboard(),
                List.of(TestModels.digestItem("Endorsed story", "https://example.com/endorsed",
                        "Hacker News", "Java/JVM", "NEWS", 9, 100, "summary", "why")),
                List.of(new Retrospective.RecurringStory("Recurring story", "https://example.com/rec", 5)),
                List.of(new SourceYield("GitHub Releases", 12, 3, 0.25)));

        String html = builder.buildHtml(full);

        assertThat(html).contains("Endorsed story").contains("https://example.com/endorsed")
                .contains("Recurring story").contains("5 wydań")
                .as("the yield line rounds the ratio to whole percent")
                .contains("GitHub Releases: 12 opublikowanych, 3 wysokiej rangi (25%)");
    }

    @Test
    void aHostileTitleCannotLeaveThePageAsMarkupAndAJavascriptUrlCannotLeaveItAsALink() {
        // The retrospective is built from stored editions, so its titles are whatever a model wrote
        // months ago from whatever a stranger published. Both defences are the digest's own
        // (escapeHtml / safeHref); this asserts the retrospective actually uses them.
        PredictionScoreboard board = scoreboard(
                new PredictionOutcome("<script>alert(1)</script>", "javascript:alert(1)",
                        Instant.parse("2026-07-15T10:00:00Z"), 2, false),
                outcome("Second", 1, true),
                outcome("Third", 1, false));

        String html = builder.buildHtml(retrospective(board, List.of(), List.of(), List.of()));

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .doesNotContain("javascript:");
    }

    @Test
    void theFooterSaysWhichWindowTheQuarterWasBuiltFrom() {
        assertThat(builder.buildHtml(retrospective(scoreboard(), List.of(), List.of(), List.of())))
                .contains("Okno: 90 dni")
                .contains("żadnego pobrania na żywo");
    }

    private static Retrospective retrospective(PredictionScoreboard board,
                                               List<pl.seniordeveloper.pulsedigest.modules.market_intel
                                                       .domain.model.DigestItem> endorsed,
                                               List<Retrospective.RecurringStory> persistent,
                                               List<SourceYield> yields) {
        return new Retrospective(FROM, TO, 26, board, endorsed, persistent, yields);
    }

    private static PredictionScoreboard scoreboard(PredictionOutcome... outcomes) {
        return new PredictionScoreboard(List.of(outcomes), 90, 14);
    }

    private static PredictionOutcome outcome(String title, int laterAppearances, boolean upvoted) {
        return new PredictionOutcome(title, "https://example.com/" + laterAppearances,
                Instant.parse("2026-07-15T10:00:00Z"), laterAppearances, upvoted);
    }
}
