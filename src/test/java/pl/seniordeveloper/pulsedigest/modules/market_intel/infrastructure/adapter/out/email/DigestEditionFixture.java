package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.application.SampleEditionLoader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * One committed PulseDigest edition, loaded from {@code samples/edition.json} into the exact objects
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
public record DigestEditionFixture(
        ReportData report,
        ResearchResult research,
        LocalDate editionDate,
        String feedbackReceiverUrl,
        Map<String, Integer> netVotesBySource,
        Map<String, Integer> netVotesByCategory
) {

    // Lives on the MAIN classpath because `--dry-run` renders the same edition (N37). One file
    // with two readers beats two files that drift: a sample the dry run shows and the golden file
    // does not check would be a demo of something the product no longer does.
    private static final String RESOURCE = "/samples/edition.json";

    /** Mirrors {@code JacksonConfig}: the modules the application registers, and nothing more. */
    public static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static DigestEditionFixture load() {
        ObjectMapper mapper = mapper();
        JsonNode root = read(mapper);
        JsonNode votes = root.get("reader_votes");
        JsonNode config = root.get("config");

        // Report and research are loaded by the same code `--dry-run` uses (N37), so the demo and
        // the golden file can never be rendering different things.
        return new DigestEditionFixture(
                SampleEditionLoader.report(mapper, root),
                SampleEditionLoader.research(mapper, root),
                LocalDate.parse(root.get("edition_date").asText()),
                config.get("feedback_receiver_url").asText(),
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
}
