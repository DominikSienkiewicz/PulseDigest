package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * How broad a run turned out to be, read from its own fetch ledger and nothing else.
 *
 * <p>Every answer here is a pure function of {@link ResearchResult#sourceFetchReports()}, which is
 * what makes this the right place for them: the F10 floor, the failure alert's sentence and the
 * degraded-run warning all have to agree about how many sources were tried and how many brought
 * something back, and they can only disagree if each works the arithmetic out for itself.
 */
@Slf4j
public final class SourceBreadth {

    private SourceBreadth() {
    }

    /**
     * Sources that actually brought something back — the number the F10 floor is measured against.
     *
     * <p>Counted from the run's own fetch ledger rather than from {@code activeSourceCount()}: that
     * one counts <em>sections</em>, and sixteen sections do not map one to one onto nineteen fetches
     * (the three X searches share a section, as do all RSS feeds), so a section count cannot say how
     * many sources are alive. {@code DISABLED} entries can never be counted here, because a source
     * nobody fetched returned nothing — and a {@code SUCCESS} that returned zero items is not
     * counted either: a reachable but silent source adds no awareness, which is the thing the floor
     * exists to measure.
     */
    public static int active(ResearchResult research) {
        return (int) research.sourceFetchReports().stream()
                .filter(report -> report.status() == SourceFetchStatus.SUCCESS && report.itemCount() > 0)
                .count();
    }

    /**
     * Sources this run tried. {@code DISABLED} is excluded deliberately: switching a source off (K7)
     * is a decision rather than a degradation, so it must neither count as a silent source nor spend
     * part of the breadth the floor asks for.
     */
    public static int attempted(ResearchResult research) {
        return research.sourceFetchReports().size() - countByStatus(research, SourceFetchStatus.DISABLED);
    }

    /**
     * What the failure alert says about the intake. It used to claim a 24h window — a window that
     * stopped existing when the lookbacks became per source (up to 104h), which made the only number
     * in the sentence the one thing in it that was false. These counts come from the run's own fetch
     * ledger, so the alert can state nothing the ledger did not record, and name no window at all.
     */
    public static String shortfallDetail(ResearchResult research, String headline, int active, int attempted) {
        int disabled = countByStatus(research, SourceFetchStatus.DISABLED);
        String disabledNote = disabled > 0 ? ", wyłączonych konfiguracją: %d".formatted(disabled) : "";
        return "%s: nic nie zwróciło %d z %d odpytanych źródeł%s"
                .formatted(headline, attempted - active, attempted, disabledNote);
    }

    /**
     * Logs a WARN when one or more sources failed this run, so a degraded digest (e.g. 15 of 18
     * sources down but one returned data) is visible in the logs rather than shipping silently as if
     * the run had been healthy.
     */
    public static void warnOnDegradedSources(String jobId, ResearchResult research) {
        List<SourceFetchReport> failed = research.sourceFetchReports().stream()
                .filter(r -> r.status() == SourceFetchStatus.FAILED)
                .toList();
        if (failed.isEmpty()) {
            return;
        }
        log.warn("[{}] Degraded run: {}/{} sources failed — {}", jobId, failed.size(),
                research.sourceFetchReports().size(),
                failed.stream().map(SourceFetchReport::sourceName).collect(Collectors.joining(", ")));
    }

    private static int countByStatus(ResearchResult research, SourceFetchStatus status) {
        return (int) research.sourceFetchReports().stream()
                .filter(report -> report.status() == status)
                .count();
    }
}
