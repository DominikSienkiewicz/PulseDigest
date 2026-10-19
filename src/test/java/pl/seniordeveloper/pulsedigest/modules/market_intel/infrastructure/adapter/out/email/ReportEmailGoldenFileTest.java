package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SectionsPolicy;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ScoringPolicy;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SignalScoringService;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
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
    private static final Path THIN_GOLDEN_FILE = Path.of("docs", "sample-digest-thin.html");
    private static final Path TEXT_GOLDEN_FILE = Path.of("docs", "sample-digest.txt");
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

    /**
     * N03. The same fixture with everything above the Must-know bar removed must render the
     * three-line note, not a document of leftovers — and the second golden file is what keeps that
     * shape from rotting, since it is a shape the normal fixture can never exercise.
     *
     * <p>The thin edition is derived rather than committed as a second fixture: it must be the same
     * edition minus its strong stories, or the comparison would be measuring two unrelated inputs.
     */
    @Test
    void anEditionWithNothingAboveTheBarRendersTheThinWeekNote() {
        ReportData thin = fixture.report().withSignals(List.of());
        List<DigestItem> weakOnly = thin.items().stream()
                .filter(item -> item.score() < DigestLayout.MUST_KNOW_THRESHOLD)
                .toList();
        String rendered = atEditionDate(builder()
                .buildHtml(new ReportData(thin.emailPreview(), thin.editorial(), thin.topInsights(), weakOnly),
                        fixture.research()));

        if (System.getProperty(UPDATE_FLAG) != null) {
            write(rendered, THIN_GOLDEN_FILE);
            fail("Re-recorded %s. Review the diff, then re-run without -Pupdate-golden to confirm it.",
                    THIN_GOLDEN_FILE);
        }
        assertThat(rendered)
                .as("a thin edition must not render tables, watchlist or recap")
                .isEqualTo(read(THIN_GOLDEN_FILE))
                .contains("Cicho w tym tygodniu")
                .doesNotContain("Krytyczne trendy");
    }

    /**
     * N14. The text part is under the golden file for the same reason the HTML is, and with one extra
     * reason on top: two renderers of one edition drift, and the drift is invisible until a reader
     * whose client prefers text gets a different digest from the one that was reviewed.
     */
    @Test
    void rendersTheFixtureEditionAsTextExactlyAsRecorded() {
        String rendered = atEditionDate(textBuilder().buildText(fixture.report(), fixture.research()));

        if (System.getProperty(UPDATE_FLAG) != null) {
            write(rendered, TEXT_GOLDEN_FILE);
            fail("Re-recorded %s. Review the diff, then re-run without -Pupdate-golden to confirm it.",
                    TEXT_GOLDEN_FILE);
        }
        assertThat(rendered).isEqualTo(read(TEXT_GOLDEN_FILE));
    }

    @Test
    void theTextPartCarriesEveryStoryTheHtmlDoes() {
        // The failure this guards is a section added to one renderer and forgotten in the other. URLs
        // compare cleanly where titles would drown in markup differences.
        String text = textBuilder().buildText(fixture.report(), fixture.research());
        String html = builder().buildHtml(fixture.report(), fixture.research());

        List<String> missing = fixture.report().items().stream()
                .map(DigestItem::url)
                .filter(html::contains)
                .filter(url -> !text.contains(url))
                .toList();

        assertThat(missing).as("stories rendered in HTML but absent from the text part").isEmpty();
    }

    @Test
    void theTextPartWrapsToATerminalWidth() {
        // The point of a text part is that it reads in a terminal. URLs are exempt: breaking one
        // makes it unclickable, which is a worse defect than a long line.
        String text = textBuilder().buildText(fixture.report(), fixture.research());

        assertThat(text.lines().filter(line -> line.length() > 78).toList())
                .allSatisfy(line -> assertThat(line).contains("http"));
    }

    @Test
    void theThinWeekSubjectSaysSoInsteadOfPromisingADigest() {
        ReportData thin = new ReportData(fixture.report().emailPreview(), fixture.report().editorial(),
                fixture.report().topInsights(), List.of());

        assertThat(builder().buildSubject(thin)).contains("cicho w tym tygodniu");
    }

    @Test
    void fixtureSignalsAreValuesTheRealScorerCanProduce() {
        // A fixture whose numbers no production run could generate is worse than none: it freezes a
        // shape the pipeline never emits, and the golden file then guards fiction.
        List<Signal> derived = new SignalScoringService(ScoringPolicy.defaults())
                .score(fixture.report().items(), fixture.netVotesBySource(), fixture.netVotesByCategory());

        assertThat(describe(derived)).isEqualTo(describe(fixture.report().signals()));
    }

    private PlainTextDigestBuilder textBuilder() {
        return new PlainTextDigestBuilder(new ThinWeekProperties(true, 3));
    }

    private ReportEmailBuilder builder() {
        // Signing stays off so the expected file is fully deterministic: the HMAC covers the edition
        // date, so a signed link would change every day. FeedbackLinkBuilderTest owns the signed form.
        return new ReportEmailBuilder(
                new FeedbackProperties(true, 30, fixture.feedbackReceiverUrl(), ""),
                new WatchlistProperties(true, fixture.watchlistTechnologies()), new ThinWeekProperties(true, 3),
                new SectionsPolicy(java.util.Set.of()));
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

        String dated = html.replace(renderedDisplay, recordedDisplay);
        if (!html.contains("edition=" + today)) {
            // The text part carries no feedback links, so it has no edition stamp to rewrite.
            return dated;
        }
        return dated.replace("edition=" + today, "edition=" + fixture.editionDate());
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
        return read(GOLDEN_FILE);
    }

    private static String read(Path file) {
        try {
            return Files.readString(goldenFile(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing golden file — re-record it with -Pupdate-golden", e);
        }
    }

    private static void write(String html) {
        write(html, GOLDEN_FILE);
    }

    private static void write(String html, Path file) {
        try {
            Files.writeString(goldenFile(file), html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not re-record the golden file", e);
        }
    }

    /** Resolved against the repository root so the test does not depend on the runner's working directory. */
    private static Path goldenFile(Path file) {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the repository root from the working directory");
        }
        return candidate.resolve(file);
    }
}
