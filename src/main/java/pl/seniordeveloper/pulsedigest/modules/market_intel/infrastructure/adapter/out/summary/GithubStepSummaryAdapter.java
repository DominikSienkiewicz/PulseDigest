package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.summary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.RunSummaryPort;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Writes the run summary to the file GitHub Actions names in {@code GITHUB_STEP_SUMMARY}, which the
 * Actions UI renders on the run's own page.
 *
 * <p>Outside Actions that variable is unset and every method here is a no-op, so a local run behaves
 * exactly as before. Token and cost figures are read from the Micrometer counters
 * {@code LlmUsageMeter} populates: the summary is a presentation of meters that already exist
 * rather than a second accounting of the same numbers.
 *
 * <p>The target is a property whose default is the Actions variable (N61). It used to be
 * {@code System.getenv} read inline, which left this class with no seam at all: thirty-seven lines
 * and fourteen branches that no test could reach, including the two that decide whether a figure is
 * printed as a number, as an em dash, or as "unpriced". Making it configuration is not a test hook —
 * it is how every other path in this project is pointed at a file — and the behaviour is unchanged:
 * blank means write nothing.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GithubStepSummaryAdapter implements RunSummaryPort {

    /**
     * The registry the figures are read from, injected rather than reached for through Micrometer's
     * global composite — the same decision, for the same reason, that {@code CostCeiling} documents.
     * That global is shared with every other test in the JVM, and this class's first test proved what
     * that costs: it passed alone and reported another suite's 22712 prompt tokens in the full run.
     * Production wiring is unchanged, because the injected registry is the one attached to the global.
     */
    private final MeterRegistry registry;

    /**
     * Where the summary is appended. The default is GitHub Actions' own variable, so nothing had to
     * change in the workflow; a blank value — the case on any machine that is not an Actions runner —
     * means every method here writes nothing.
     */
    @Value("${report.run-summary-path:${GITHUB_STEP_SUMMARY:}}")
    private String summaryPath;

    @Override
    public void writeIntake(ResearchResult research, int publishedItems, int criticalItems) {
        List<SourceFetchReport> reports = research != null && research.sourceFetchReports() != null
                ? research.sourceFetchReports() : List.of();

        StringBuilder md = new StringBuilder("## PulseDigest run\n\n");
        md.append("| Metric | Value |\n|---|---|\n")
                .append("| Items published | ").append(publishedItems).append(" |\n")
                .append("| 🔴 Critical | ").append(criticalItems).append(" |\n")
                .append("| Sources fetched | ").append(reports.size()).append(" |\n")
                .append("| Prompt tokens | ").append(tokens("prompt")).append(" |\n")
                .append("| Completion tokens | ").append(tokens("completion")).append(" |\n")
                .append("| LLM cost | ").append(cost()).append(" |\n\n");

        md.append("### Per-source intake\n\n| Source | Status | Items |\n|---|---|---|\n");
        reports.stream()
                .sorted(Comparator.comparingInt(SourceFetchReport::itemCount)
                        .thenComparing(SourceFetchReport::sourceName))
                .forEach(r -> md.append("| ").append(r.sourceName())
                        .append(" | ").append(r.status())
                        .append(" | ").append(r.itemCount()).append(" |\n"));
        md.append('\n');

        append(md.toString());
    }

    @Override
    public void writeOutcome(String status, String detail) {
        // A literal LF, not %n: this file is read by GitHub as Markdown, so the separator is the
        // format's, not the host's.
        append("### Outcome: " + status + "\n\n" + (detail == null ? "" : detail) + "\n");
    }

    private String tokens(String kind) {
        double total = registry.find("llm.tokens").tag("kind", kind).counters()
                .stream().mapToDouble(Counter::count).sum();
        return total == 0 ? "—" : String.valueOf(Math.round(total));
    }

    /**
     * Absent rather than zero when no price list is configured — see {@code LlmProperties}.
     *
     * <p>{@code Locale.ROOT} is not decoration: without it the decimal separator follows whatever
     * locale the JVM happens to start in, so the same run printed {@code $0,0125} on the author's
     * machine and {@code $0.0125} on an Actions runner. A figure that changes shape with the host is
     * not a figure anything can parse, and this one is read by people and greps alike.
     *
     * <p>{@code String.format} rather than {@code "…".formatted(…)}: the instance method has no
     * locale overload, so a {@code Locale} handed to it is just another argument for {@code %f}.
     */
    private String cost() {
        List<Counter> counters = List.copyOf(registry.find("llm.cost.usd").counters());
        if (counters.isEmpty()) {
            return "unpriced";
        }
        return String.format(Locale.ROOT, "$%.4f", counters.stream().mapToDouble(Counter::count).sum());
    }

    private void append(String markdown) {
        String target = summaryPath;
        if (target == null || target.isBlank()) {
            return;
        }
        try {
            Files.writeString(Path.of(target), markdown, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Could not write the run summary to {}: {}", target, e.getMessage());
        }
    }
}
