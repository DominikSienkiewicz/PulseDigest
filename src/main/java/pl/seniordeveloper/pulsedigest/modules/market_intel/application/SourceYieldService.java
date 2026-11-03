package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceFetchStatus;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceIntake;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceWeights;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceYield;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures what each source returned on the prompt slots it was given.
 *
 * <p>The digest allocates 100 prompt slots by static per-source caps and has never known whether
 * Reddit ever reached Must-know. This is the ledger that answers it. It only reports — reallocating
 * caps from these numbers is a self-reinforcing loop (a starved source cannot prove its worth) and
 * needs a mandatory slot floor before it would be safe.
 */
@Slf4j
@Service
public class SourceYieldService {

    /** Editions a source must be silent across before the run log calls it out. */
    private static final int SILENT_SOURCE_EDITIONS = 3;

    /** A story that reached CRITICAL or STRONG is a slot that paid for itself. */
    private static boolean isHighRank(SignalRank rank) {
        return rank == SignalRank.CRITICAL || rank == SignalRank.STRONG;
    }

    /** Per-source yield over the given editions, best-yielding first. */
    public List<SourceYield> scoreboard(List<PastEdition> editions) {
        Map<String, int[]> tally = new LinkedHashMap<>();
        for (PastEdition edition : editions) {
            for (Signal signal : edition.signals()) {
                String source = signal.item().source();
                if (source == null || source.isBlank()) {
                    continue;
                }
                int[] counts = tally.computeIfAbsent(SourceWeights.keyOf(source), key -> new int[2]);
                counts[0]++;
                if (isHighRank(signal.rank())) {
                    counts[1]++;
                }
            }
        }
        return tally.entrySet().stream()
                .map(e -> new SourceYield(e.getKey(), e.getValue()[0], e.getValue()[1],
                        (double) e.getValue()[1] / e.getValue()[0]))
                .sorted(Comparator.comparingDouble(SourceYield::yieldRatio).reversed()
                        .thenComparing(Comparator.comparingInt(SourceYield::appearances).reversed()))
                .toList();
    }

    /**
     * Per-source intake over the given editions — every source that was fetched, including the ones
     * that returned nothing. Silent sources sort first, because they are the reason this exists.
     *
     * <p>Only successful fetches are counted. A source erroring out is already visible: the run log
     * warns on degraded sources and the digest footer counts them. The case that was invisible, and
     * the one this measures, is the HTTP 200 that returns an empty result set.
     */
    public List<SourceIntake> intakeLedger(List<PastEdition> editions) {
        Map<String, int[]> tally = new LinkedHashMap<>();
        for (PastEdition edition : editions) {
            for (SourceFetchReport report : edition.fetchReports()) {
                if (report == null || report.status() != SourceFetchStatus.SUCCESS) {
                    continue;
                }
                String source = report.sourceName();
                if (source != null && !source.isBlank()) {
                    int[] counts = tally.computeIfAbsent(source, key -> new int[3]);
                    counts[0]++;
                    if (report.itemCount() == 0) {
                        counts[1]++;
                    }
                    counts[2] += report.itemCount();
                }
            }
        }
        return tally.entrySet().stream()
                .map(e -> new SourceIntake(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingInt(SourceIntake::itemsFetched)
                        .thenComparing(SourceIntake::source))
                .toList();
    }

    /**
     * Sources fetched successfully in at least {@code minEditions} editions of the window that
     * returned nothing in every one of them.
     *
     * <p>The window, not a consecutive run: a source silent across the whole history read is the
     * same conclusion and does not need the editions ordered. {@code minEditions} is what keeps a
     * source that was merely quiet for one run from being called dead.
     */
    public List<SourceIntake> silentSources(List<PastEdition> editions, int minEditions) {
        return intakeLedger(editions).stream()
                .filter(SourceIntake::isSilent)
                .filter(intake -> intake.editions() >= minEditions)
                .toList();
    }

    /**
     * The sources the digest should say are not pulling their weight, at the service's own
     * threshold — so the footer and the run log can never disagree about who is silent (N27).
     */
    public List<SourceIntake> silentSources(List<PastEdition> editions) {
        return silentSources(editions, SILENT_SOURCE_EDITIONS);
    }

    /**
     * Dumps both ledgers to the run log, where GitHub Actions keeps them, and warns by name on any
     * source that has gone quiet.
     *
     * <p>The WARN is the point of F7: on a green run nobody reads an INFO line, and a source that
     * fetches 200 OK with an empty body produced no line at all before this. Three editions is the
     * threshold the audit asks for — roughly a week and a half at the Mon/Thu cadence, long enough
     * that a single thin run does not raise it.
     */
    public void logScoreboard(List<PastEdition> editions) {
        List<SourceIntake> intake = intakeLedger(editions);
        if (!intake.isEmpty()) {
            log.info("=== Source intake over {} past edition(s) ===", editions.size());
            intake.forEach(i -> log.info("source-intake source={} editions={} silent_editions={} items={}",
                    i.source(), i.editions(), i.silentEditions(), i.itemsFetched()));
        }

        silentSources(editions, SILENT_SOURCE_EDITIONS).forEach(i -> log.warn(
                "source-intake SILENT source={} — fetched 0 items in all {} edition(s) of the window; "
                        + "it is reachable and returning nothing, which no failure path reports",
                i.source(), i.editions()));

        List<SourceYield> yields = scoreboard(editions);
        if (yields.isEmpty()) {
            return;
        }
        log.info("=== Source yield over {} past edition(s) ===", editions.size());
        yields.forEach(y -> log.info("source-yield source={} published={} high_rank={} yield={}%",
                y.source(), y.appearances(), y.highRankAppearances(), Math.round(y.yieldRatio() * 100)));
    }
}
