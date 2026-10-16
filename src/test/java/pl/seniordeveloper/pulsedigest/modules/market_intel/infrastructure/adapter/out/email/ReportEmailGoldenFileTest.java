package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Renders the committed fixture edition through the real builder chain and diffs the whole document
 * against {@code docs/sample-digest.html}.
 *
 * <p>Twelve classes turn a {@code ReportData} into the delivered HTML and, until this file existed,
 * every one of them was asserted on substrings only. Any edit that reordered the sections, changed a
 * colour, dropped a feedback link or altered a formatting helper shipped unverified. A whole-document
 * diff is the only assertion that cannot be satisfied by markup that merely still contains the right
 * fragments.
 *
 * <p>The expected file is also the sample the README shows, deliberately: the screenshot in the
 * README cannot go stale without this test going red first.
 *
 * <h2>Re-recording</h2>
 * <pre>./gradlew test --tests '*ReportEmailGoldenFileTest' -Pupdate-golden</pre>
 * The flag rewrites the expected file and then <em>fails</em> the run on purpose. Re-recording has to
 * be a decision someone makes and then confirms with a second, clean run — never a side effect of a
 * green build. Read the diff before you commit it; that diff is the review.
 *
 * <p>When the diff changes anything above the "Deals &amp; Tools" heading, refresh the README's
 * screenshot from the same file in the same commit, so the two cannot disagree:
 * <pre>
 * "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
 *   --hide-scrollbars --force-device-scale-factor=1.5 --window-size=800,1040 \
 *   --screenshot=docs/images/sample-digest.png "file://$PWD/docs/sample-digest.html"
 * </pre>
 */
class ReportEmailGoldenFileTest {

    private static final Path GOLDEN_FILE = Path.of("docs", "sample-digest.html");
    private static final String UPDATE_FLAG = "pulsedigest.golden.update";
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void rendersTheFixtureEditionExactlyAsRecordedInTheGoldenFile() {
        String rendered = atEditionDate(builder().buildHtml(fixture.report(), fixture.research()));

        if (System.getProperty(UPDATE_FLAG) != null) {
            write(rendered);
            fail("Re-recorded %s. Review the diff, then re-run without -Pupdate-golden to confirm it.",
                    GOLDEN_FILE);
        }
        assertThat(rendered).isEqualTo(read());
    }

    @Test
    void fixtureSignalsAreValuesTheRealScorerCanProduce() {
        // A fixture whose numbers no production run could generate is worse than none: it freezes a
        // shape the pipeline never emits, and the golden file then guards fiction.
        List<Signal> derived = new SignalScoringService()
                .score(fixture.report().items(), fixture.netVotesBySource(), fixture.netVotesByCategory());

        assertThat(describe(derived)).isEqualTo(describe(fixture.report().signals()));
    }

    private ReportEmailBuilder builder() {
        // Signing stays off so the expected file is fully deterministic: the HMAC covers the edition
        // date, so a signed link would change every day. FeedbackLinkBuilderTest owns the signed form.
        return new ReportEmailBuilder(
                new FeedbackProperties(true, 30, fixture.feedbackReceiverUrl(), ""),
                new WatchlistProperties(true, fixture.watchlistTechnologies()));
    }

    /**
     * Rewrites today's two rendered dates to the edition date the golden file was recorded for.
     *
     * <p>{@code ReportEmailBuilder} reads {@code LocalDate.now(UTC)} directly — for the display date in
     * the title and header, and for the ISO edition stamp carried by every feedback link — so a render
     * made today cannot be compared to a committed file without this step. Both substitutions are
     * asserted rather than assumed: if a date ever stops being formatted this way, the test says so
     * here instead of failing later with an unreadable diff.
     */
    private String atEditionDate(String html) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String renderedDisplay = today.format(DISPLAY_DATE);
        String recordedDisplay = fixture.editionDate().format(DISPLAY_DATE);

        assertThat(html)
                .as("rendered mail must carry today's display date %s", renderedDisplay)
                .contains(renderedDisplay);
        assertThat(html)
                .as("every feedback link must carry today's ISO edition stamp %s", today)
                .contains("edition=" + today);

        return html
                .replace(renderedDisplay, recordedDisplay)
                .replace("edition=" + today, "edition=" + fixture.editionDate());
    }

    /**
     * One line per signal, in order — rank, score, confirming domains and the whole score breakdown.
     * A list of lines diffs readably where fifteen nested objects would not, and formatting the weights
     * to two decimals compares them at the precision the scorer's 0.05 and 0.02 steps actually carry.
     */
    private static List<String> describe(List<Signal> signals) {
        return signals.stream().map(ReportEmailGoldenFileTest::describe).toList();
    }

    private static String describe(Signal signal) {
        ScoreBreakdown breakdown = signal.breakdown();
        return "%s %d [%s] %s %.2f->%.2f votes=%d engagement=+%d category=%.2f/%d cross=+%d %s".formatted(
                signal.rank(), signal.signalScore(),
                signal.sourceDomains().stream().map(SourceDomain::name).collect(Collectors.joining(",")),
                breakdown.sourceKey(), breakdown.baseWeight(), breakdown.effectiveWeight(),
                breakdown.netSourceVotes(), breakdown.engagementBonus(),
                breakdown.categoryMultiplier(), breakdown.netCategoryVotes(),
                breakdown.crossSourceBonus(), signal.item().url());
    }

    private static String read() {
        try {
            return Files.readString(goldenFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing golden file — re-record it with -Pupdate-golden", e);
        }
    }

    private static void write(String html) {
        try {
            Files.writeString(goldenFile(), html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not re-record the golden file", e);
        }
    }

    /** Resolved against the repository root so the test does not depend on the runner's working directory. */
    private static Path goldenFile() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the repository root from the working directory");
        }
        return candidate.resolve(GOLDEN_FILE);
    }
}
