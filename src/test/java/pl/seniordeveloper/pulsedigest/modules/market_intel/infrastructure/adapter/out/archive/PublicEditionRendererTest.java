package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Calibration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyCoordinate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DependencyRelease;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .DigestEditionFixture;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N31. The test that has to be right, because publishing cannot be undone.
 *
 * <p>Everything here is asserted against a <em>fully populated</em> edition — one carrying a reader
 * profile, a calibration, a saved shelf, pinned dependencies, stack markers and a signing secret —
 * because a renderer that strips nothing passes trivially against a fixture that contains nothing.
 */
class PublicEditionRendererTest {

    private static final String RECEIVER = "https://receiver.example.invalid/feedback";

    private final DigestEditionFixture fixture = DigestEditionFixture.load();
    private final PublicEditionRenderer renderer = new PublicEditionRenderer(new ThinWeekProperties(true, 3));

    @Test
    void noSignedFeedbackLinkIsEverPublished() {
        // The whole reason this class exists. The HMAC covers values printed beside it, so a signed
        // link in a public archive is a forgeable vote handed to every reader of the page.
        String html = renderer.render(populated(), fixture.research());

        assertThat(html)
                .doesNotContain("sig=")
                .doesNotContain("vote=")
                .doesNotContain("edition=")
                .doesNotContain(RECEIVER);
    }

    @Test
    void nothingTheDigestLearnedAboutTheReaderIsPublished() {
        String html = renderer.render(populated(), fixture.research());

        assertThat(html)
                .as("the reader's own voting history is printed inline in score explanations")
                .doesNotContain("Twoje głosy")
                .doesNotContain("Dlaczego to widzisz")
                .as("the saved shelf is a list of one person's bookmarks")
                .doesNotContain("Zapisane na później")
                .doesNotContain("https://example.com/bookmarked");
    }

    @Test
    void nothingAboutTheReadersOwnStackIsPublished() {
        // Three features describe the reader's dependencies. Publishing them is both none of the
        // public's business and a small reconnaissance gift.
        String html = renderer.render(populated(), fixture.research());

        assertThat(html)
                .doesNotContain("Twoje zależności")
                .doesNotContain("pinujesz")
                .doesNotContain("com.example:private-lib")
                .doesNotContain("Koniec wsparcia");
    }

    @Test
    void theNewsItselfSurvives() {
        // Stripping is only correct if what is left is still an edition.
        String html = renderer.render(populated(), fixture.research());

        assertThat(html)
                .contains("PulseDigest")
                .contains("Must-know")
                // Compared on a fragment without an ampersand: the renderer escapes titles, and a
                // test that forgot would be testing its own expectations rather than the output.
                .contains("Introducing the MCP Tool Registry");
    }

    @Test
    void depersonalisingIsDoneOnTheReportNotOnTheMarkup() {
        // A regex over rendered HTML is a guess; emptying the report is a fact. This asserts the
        // mechanism, so a future "just filter the output" refactor fails here.
        ReportData clean = PublicEditionRenderer.depersonalise(populated());

        assertThat(clean.readerProfile()).isNull();
        assertThat(clean.calibration()).isNull();
        assertThat(clean.savedBacklog()).isNull();
        assertThat(clean.dependencyReleases()).isEmpty();
        assertThat(clean.stackAffectingUrls()).isEmpty();
        assertThat(clean.eolNotices()).isEmpty();
    }

    @Test
    void publicInformationIsKept() {
        ReportData clean = PublicEditionRenderer.depersonalise(populated());

        assertThat(clean.callsForPapers())
                .as("a conference deadline is public by nature")
                .isNotEmpty();
        assertThat(clean.items()).isNotEmpty();
        assertThat(clean.editorial()).isNotBlank();
    }

    /** The fixture with every private field it can carry actually carrying something. */
    private ReportData populated() {
        LocalDate today = LocalDate.now();
        return fixture.report()
                .withCalibration(new Calibration(9, 6))
                .withSavedBacklog(new SavedBacklog(List.of(new SavedItem("Bookmarked",
                        "https://example.com/bookmarked", "Hacker News", "Java/JVM",
                        Instant.now().minusSeconds(86_400))), 1))
                .withDependencyReleases(List.of(new DependencyRelease(
                        new DependencyCoordinate("com.example", "private-lib", "1.0.0", "build.gradle.kts"),
                        "1.2.0")))
                .withStackAffectingUrls(Set.of(fixture.report().items().getFirst().url()))
                .withEolNotices(List.of(new EolNotice("Eclipse Temurin 26", today.minusMonths(1), today)))
                .withCallsForPapers(List.of(new CallForPapers("Devnexus",
                        "https://sessionize.com/devnexus", today.plusDays(20), "Atlanta")));
    }
}
