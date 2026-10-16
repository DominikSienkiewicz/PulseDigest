package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ScoreBreakdown;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SourceDomain;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendRecurrence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.TrendVelocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One committed PulseDigest edition, loaded from {@code fixtures/edition.json} into the exact objects
 * {@code ReportEmailBuilder} is handed in production.
 *
 * <p>The fixture exists because nothing else in this repository records what the product produces.
 * Every email section is a pure function of a {@code ReportData} and a {@code ResearchResult}, so one
 * realistic pair is enough to render a whole digest — and enough for a golden file to fail on any
 * change to the markup, the section order or the formatting helpers.
 *
 * <p>The only shape that deviates from production JSON is {@code signals[]}, whose entries name their
 * item by {@code url} rather than carrying a second copy of the whole {@code DigestItem}. A URL with
 * no matching item is a broken fixture and fails here rather than quietly rendering a thinner mail.
 */
record DigestEditionFixture(
        ReportData report,
        ResearchResult research,
        LocalDate editionDate,
        String feedbackReceiverUrl,
        List<String> watchlistTechnologies,
        Map<String, Integer> netVotesBySource,
        Map<String, Integer> netVotesByCategory
) {

    private static final String RESOURCE = "/fixtures/edition.json";

    /** Mirrors {@code JacksonConfig}: the modules the application registers, and nothing more. */
    static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    static DigestEditionFixture load() {
        ObjectMapper mapper = mapper();
        JsonNode root = read(mapper);
        ObjectNode reportNode = (ObjectNode) root.get("report");
        JsonNode signalsNode = reportNode.remove("signals");
        JsonNode votes = root.get("reader_votes");
        JsonNode config = root.get("config");

        ReportData report = mapper.convertValue(reportNode, ReportData.class);
        List<FixtureSignal> stored = mapper.convertValue(signalsNode, new TypeReference<List<FixtureSignal>>() { });

        return new DigestEditionFixture(
                report.withSignals(attachItems(stored, report.items())),
                mapper.convertValue(root.get("research"), ResearchResult.class),
                LocalDate.parse(root.get("edition_date").asText()),
                config.get("feedback_receiver_url").asText(),
                mapper.convertValue(config.get("watchlist_technologies"), new TypeReference<List<String>>() { }),
                mapper.convertValue(votes.get("by_source"), new TypeReference<Map<String, Integer>>() { }),
                mapper.convertValue(votes.get("by_category"), new TypeReference<Map<String, Integer>>() { }));
    }

    private static JsonNode read(ObjectMapper mapper) {
        try (InputStream in = DigestEditionFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not on the test classpath: " + RESOURCE);
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    private static List<Signal> attachItems(List<FixtureSignal> stored, List<DigestItem> items) {
        Map<String, DigestItem> itemsByUrl = items.stream()
                .collect(Collectors.toMap(DigestItem::url, Function.identity(), (first, second) -> first));
        return stored.stream().map(signal -> signal.toSignal(itemsByUrl)).toList();
    }

    /**
     * A {@code signals[]} entry as the fixture stores it: the scoring outcome, plus the URL of the item
     * it belongs to instead of a second copy of that item.
     */
    private record FixtureSignal(
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
                throw new IllegalStateException("Fixture signal references an unknown item url: " + url);
            }
            return new Signal(item, rank, signalScore, sourceDomains, recurrence, velocity, breakdown);
        }
    }
}
