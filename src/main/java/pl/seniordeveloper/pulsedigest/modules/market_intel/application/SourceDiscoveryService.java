package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceProposal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.SourceDiscoveryPort;

import java.util.Comparator;
import java.util.List;

/**
 * Proposes sources from the record of what already worked (N28).
 *
 * <p>Run on demand — {@code --discover-sources} — rather than on every edition. The evidence is
 * months of published items, which does not change meaningfully week to week, and a proposal that
 * arrived twice a week would be ignored by the third one.
 *
 * <p>Capped at three, and every proposal must carry the items that justify it. Both rules exist for
 * the same reason: an unbounded list of unevidenced suggestions is the failure mode that makes
 * features like this get switched off.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SourceDiscoveryService {

    private static final int MAX_PROPOSALS = 3;
    private static final int EVIDENCE_ITEMS = 40;

    private final ReportHistoryPort reportHistoryPort;
    private final SourceDiscoveryPort sourceDiscoveryPort;
    private final SourceYieldService sourceYieldService;

    public List<SourceProposal> discover(int lookbackDays) {
        List<PastEdition> editions = reportHistoryPort.recentEditions(lookbackDays);
        if (editions.isEmpty()) {
            log.warn("source-discovery window={}d has no stored editions — nothing to argue from",
                    lookbackDays);
            return List.of();
        }
        List<DigestItem> evidence = bestItems(editions);
        if (evidence.isEmpty()) {
            log.warn("source-discovery found no high-ranked items to reason from");
            return List.of();
        }

        List<SourceProposal> proposals = sourceDiscoveryPort.propose(evidence).stream()
                .filter(SourceProposal::isSupported)
                .limit(MAX_PROPOSALS)
                .toList();

        log.info("=== Source proposals from {} edition(s), {} high-ranked item(s) ===",
                editions.size(), evidence.size());
        sourceYieldService.scoreboard(editions).forEach(y ->
                log.info("source-discovery existing source={} published={} high_rank={}",
                        y.source(), y.appearances(), y.highRankAppearances()));
        proposals.forEach(p -> log.info("source-discovery PROPOSAL name={} kind={} why={} evidence={}",
                p.name(), p.kind(), p.why(), p.evidence()));
        if (proposals.isEmpty()) {
            log.info("source-discovery proposed nothing — the published record does not argue for a "
                    + "source that is missing");
        }
        return proposals;
    }

    /** The items that earned their place, newest first — the only ones worth arguing from. */
    private static List<DigestItem> bestItems(List<PastEdition> editions) {
        return editions.stream()
                .sorted(Comparator.comparing(PastEdition::generatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .flatMap(edition -> edition.signals().stream())
                .filter(signal -> signal.rank() == SignalRank.CRITICAL || signal.rank() == SignalRank.STRONG)
                .map(Signal::item)
                .limit(EVIDENCE_ITEMS)
                .toList();
    }
}
