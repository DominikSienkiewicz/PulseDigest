package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
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

    /** Dumps the ledger to the run log, where GitHub Actions keeps it. */
    public void logScoreboard(List<PastEdition> editions) {
        List<SourceYield> yields = scoreboard(editions);
        if (yields.isEmpty()) {
            return;
        }
        log.info("=== Source yield over {} past edition(s) ===", editions.size());
        yields.forEach(y -> log.info("source-yield source={} published={} high_rank={} yield={}%",
                y.source(), y.appearances(), y.highRankAppearances(), Math.round(y.yieldRatio() * 100)));
    }
}
