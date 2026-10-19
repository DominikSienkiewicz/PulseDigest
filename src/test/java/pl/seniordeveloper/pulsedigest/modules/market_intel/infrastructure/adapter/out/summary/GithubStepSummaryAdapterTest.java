package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.summary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.RawCounts;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.testing.ResearchResults;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run summary GitHub Actions renders on a run's own page (N61).
 *
 * <p>Untested until the target became a property: the path was read from {@code System.getenv} inside
 * the method that used it, so nothing could point the adapter at a file it could check. What that hid
 * is below — an append that must not truncate the previous section, a sort that decides which source
 * a reader sees first, and three formatting branches that turn missing meters into an em dash or the
 * word "unpriced" rather than into a confident zero.
 *
 * <p>Every case hands the adapter its own registry. The first version read Micrometer's global
 * composite, as the adapter then did, and it passed alone while failing in the full suite: another
 * suite's registry was still attached and the summary reported <em>its</em> 22712 prompt tokens. The
 * adapter now takes a registry, the way {@code CostCeiling} already did — the test did not get a
 * hook, the design got the fix.
 */
class GithubStepSummaryAdapterTest {

    @Test
    void writesTheRunTableAndThePerSourceIntake(@TempDir Path dir) throws Exception {
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary).writeIntake(research(
                SourceFetchReport.success("Hacker News", 7, 120L),
                SourceFetchReport.failed("Reddit", 90L, "connection timeout"),
                SourceFetchReport.disabled("Twitter/X topic")), 12, 2);

        String written = Files.readString(summary);
        assertThat(written)
                .contains("## PulseDigest run")
                .contains("| Items published | 12 |")
                .contains("| 🔴 Critical | 2 |")
                .contains("| Sources fetched | 3 |")
                .contains("### Per-source intake")
                .contains("| Hacker News | SUCCESS | 7 |")
                .contains("| Reddit | FAILED | 0 |")
                .contains("| Twitter/X topic | DISABLED | 0 |");
    }

    @Test
    void theQuietestSourcesComeFirstSoTheyAreTheOnesARunPageShows(@TempDir Path dir) throws Exception {
        // Ascending by item count, then by name: a summary is read to find out what went wrong, and
        // the sources that returned nothing are the answer to that question.
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary).writeIntake(research(
                SourceFetchReport.success("Loud", 40, 10L),
                SourceFetchReport.success("Quiet", 0, 10L),
                SourceFetchReport.success("Also quiet", 0, 10L)), 1, 0);

        String intake = Files.readString(summary);
        assertThat(intake.indexOf("| Also quiet |")).isLessThan(intake.indexOf("| Quiet |"));
        assertThat(intake.indexOf("| Quiet |")).isLessThan(intake.indexOf("| Loud |"));
    }

    @Test
    void aSecondWriteAppendsInsteadOfReplacingTheFirst(@TempDir Path dir) throws Exception {
        Path summary = dir.resolve("summary.md");
        GithubStepSummaryAdapter adapter = adapterWriting(summary);

        adapter.writeIntake(research(SourceFetchReport.success("Hacker News", 3, 10L)), 3, 1);
        adapter.writeOutcome("DELIVERED", "sent via resend");

        assertThat(Files.readString(summary))
                .as("Actions renders one file per job; truncating it would lose the run's own table")
                .contains("## PulseDigest run")
                .contains("### Outcome: DELIVERED")
                .contains("sent via resend");
    }

    @Test
    void anOutcomeWithoutDetailStillRendersItsHeading(@TempDir Path dir) throws Exception {
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary).writeOutcome("ERROR", null);

        assertThat(Files.readString(summary)).contains("### Outcome: ERROR");
    }

    @Test
    void withNoPathConfiguredNothingIsWrittenAndNothingFails(@TempDir Path dir) throws Exception {
        // The local-run case, which is the common one: no Actions variable, no property, no file.
        GithubStepSummaryAdapter adapter = new GithubStepSummaryAdapter(new SimpleMeterRegistry());
        setPath(adapter, "");

        adapter.writeIntake(research(SourceFetchReport.success("Hacker News", 1, 10L)), 1, 0);
        adapter.writeOutcome("DELIVERED", "detail");

        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void anUnwritableTargetIsLoggedRatherThanThrown(@TempDir Path dir) throws Exception {
        // A run must not fail because its own summary could not be written; the digest is the product.
        GithubStepSummaryAdapter adapter = adapterWriting(dir.resolve("no-such-directory/summary.md"));

        adapter.writeOutcome("DELIVERED", "detail");

        assertThat(dir.resolve("no-such-directory")).doesNotExist();
    }

    @Test
    void absentMetersRenderAsAnEmDashAndAnAbsentPriceListAsUnpriced(@TempDir Path dir) throws Exception {
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary).writeIntake(research(), 0, 0);

        assertThat(Files.readString(summary))
                .as("a zero would claim the run spent nothing; an em dash says nobody measured")
                .contains("| Prompt tokens | — |")
                .contains("| Completion tokens | — |")
                .contains("| LLM cost | unpriced |");
    }

    @Test
    void recordedTokensAndCostAreReportedFromTheMetersThatAlreadyExist(@TempDir Path dir)
            throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("llm.tokens", "kind", "prompt", "call", "synthesis").increment(1500);
        registry.counter("llm.tokens", "kind", "completion", "call", "synthesis").increment(400);
        registry.counter("llm.cost.usd", "call", "synthesis").increment(0.0125);
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary, registry).writeIntake(research(), 5, 1);

        assertThat(Files.readString(summary))
                .contains("| Prompt tokens | 1500 |")
                .contains("| Completion tokens | 400 |")
                .as("Locale.ROOT, or the decimal separator follows whatever locale the host started in")
                .contains("| LLM cost | $0.0125 |");
    }

    @Test
    void aRunWithNoResearchAtAllStillProducesItsTable(@TempDir Path dir) throws Exception {
        Path summary = dir.resolve("summary.md");

        adapterWriting(summary).writeIntake(null, 0, 0);

        assertThat(Files.readString(summary))
                .contains("| Sources fetched | 0 |")
                .contains("### Per-source intake");
    }

    private static GithubStepSummaryAdapter adapterWriting(Path target) throws Exception {
        return adapterWriting(target, new SimpleMeterRegistry());
    }

    private static GithubStepSummaryAdapter adapterWriting(Path target, MeterRegistry registry)
            throws Exception {
        GithubStepSummaryAdapter adapter = new GithubStepSummaryAdapter(registry);
        setPath(adapter, target.toString());
        return adapter;
    }

    private static void setPath(GithubStepSummaryAdapter adapter, String value) throws Exception {
        Field field = GithubStepSummaryAdapter.class.getDeclaredField("summaryPath");
        field.setAccessible(true);
        field.set(adapter, value);
    }

    private static ResearchResult research(SourceFetchReport... reports) {
        return ResearchResults.builder()
                .collectedAt(LocalDateTime.parse("2026-10-19T06:00:00"))
                .rawCounts(RawCounts.builder().build())
                .fetchReports(List.of(reports))
                .build();
    }
}
