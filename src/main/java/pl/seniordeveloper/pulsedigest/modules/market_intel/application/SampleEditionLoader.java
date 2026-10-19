package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the committed sample edition into the objects the email builder is handed in production.
 *
 * <p>In main rather than in test scope because two things read it: the golden-file test, and
 * {@code --dry-run} (N37). The sample stores each signal with the <em>URL</em> of the item it scored
 * instead of a second copy of that item — so it cannot drift from the items beside it — and
 * re-attaching them is the one piece of logic worth having exactly once.
 */
public final class SampleEditionLoader {

    private SampleEditionLoader() {
    }

    /** The sample's report, with its signals re-attached to the items they scored. */
    public static ReportData report(ObjectMapper mapper, JsonNode root) {
        ObjectNode reportNode = (ObjectNode) root.get("report").deepCopy();
        JsonNode signalsNode = reportNode.remove("signals");
        ReportData report = mapper.convertValue(reportNode, ReportData.class);
        List<StoredSignal> stored =
                mapper.convertValue(signalsNode, new TypeReference<List<StoredSignal>>() { });
        return report.withSignals(attachItems(stored, report.items()));
    }

    public static ResearchResult research(ObjectMapper mapper, JsonNode root) {
        return mapper.convertValue(root.get("research"), ResearchResult.class);
    }

    private static List<Signal> attachItems(List<StoredSignal> stored, List<DigestItem> items) {
        Map<String, DigestItem> itemsByUrl = items.stream()
                .collect(Collectors.toMap(DigestItem::url, Function.identity(), (first, second) -> first));
        return stored.stream().map(signal -> signal.toSignal(itemsByUrl)).toList();
    }

    /**
     * A {@code signals[]} entry as the sample stores it: the scoring outcome, plus the URL of the
     * item it belongs to rather than a second copy of that item.
     */
    private record StoredSignal(
            String url,
            SignalRank rank,
            int signalScore,
            List<SourceDomain> sourceDomains,
            TrendRecurrence recurrence,
            TrendVelocity velocity,
            ScoreBreakdown breakdown
    ) {

        Signal toSignal(Map<String, DigestItem> itemsByUrl) {
            DigestItem item = itemsByUrl.get(url);
            if (item == null) {
                throw new IllegalStateException("Sample signal references an unknown item url: " + url);
            }
            return new Signal(item, rank, signalScore, sourceDomains, recurrence, velocity, breakdown);
        }
    }
}
