package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email
        .DigestEditionFixture;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ThinWeekProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.WatchlistProperties;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N31–N33, end to end: the files a static host would serve.
 *
 * <p>The assertion that matters most is the last one — nothing anywhere in the generated site
 * carries a feedback link. The renderer is tested on its own, but the site has five file types and
 * a leak only has to happen in one of them.
 */
class ArchiveSiteFileAdapterTest {

    private static final String RECEIVER = "https://receiver.example.invalid/feedback";
    private static final String SITE = "https://example.invalid/PulseDigest/";

    private final DigestEditionFixture fixture = DigestEditionFixture.load();

    @Test
    void writesAPagePerEditionPlusIndexSearchAndFeed(@TempDir Path dir) throws Exception {
        write(dir, editions());

        assertThat(dir.resolve("index.html")).exists();
        assertThat(dir.resolve("search.html")).exists();
        assertThat(dir.resolve("search-index.json")).exists();
        assertThat(dir.resolve("feed.xml")).exists();
        assertThat(dir.resolve("2026-10-15.html")).exists();
        assertThat(dir.resolve("2026-10-12.html")).exists();
        assertThat(dir.resolve(".nojekyll"))
                .as("without it Pages runs the output through Jekyll")
                .exists();
    }

    @Test
    void theIndexCountsEditionsInPolish(@TempDir Path dir) throws Exception {
        // "3 wydań" is wrong and visible on the landing page; Polish takes "wydania" for 2–4.
        write(dir, editions());

        assertThat(Files.readString(dir.resolve("index.html"))).contains("2 wydania");
    }

    @Test
    void theIndexLinksEveryEditionNewestFirst(@TempDir Path dir) throws Exception {
        write(dir, editions());

        String index = Files.readString(dir.resolve("index.html"));
        assertThat(index).contains("2026-10-15.html", "2026-10-12.html");
        assertThat(index.indexOf("2026-10-15.html"))
                .as("newest first")
                .isLessThan(index.indexOf("2026-10-12.html"));
    }

    @Test
    void theFeedIsWellFormedAtomWithOneEntryPerEdition(@TempDir Path dir) throws Exception {
        write(dir, editions());
        String feed = Files.readString(dir.resolve("feed.xml"));

        // Parsed, not pattern-matched: a feed that does not parse is not a feed.
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(feed)));

        assertThat(document.getElementsByTagName("entry").getLength()).isEqualTo(2);
        assertThat(feed).contains(SITE + "2026-10-15.html");
    }

    @Test
    void theSearchIndexIsValidJsonCoveringEveryItem(@TempDir Path dir) throws Exception {
        write(dir, editions());

        var index = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(Files.readString(dir.resolve("search-index.json")));

        assertThat(index.isArray()).isTrue();
        assertThat(index.size()).isEqualTo(fixture.report().items().size() * 2);
        assertThat(index.get(0).get("t").asText()).isNotBlank();
        assertThat(index.get(0).get("e").asText()).isEqualTo("2026-10-15");
    }

    @Test
    void noFileInTheWholeSiteCarriesAFeedbackLink(@TempDir Path dir) throws Exception {
        // The one that cannot be allowed to fail. A signed link is a forgeable vote, and the archive
        // is the one thing here that cannot be taken back once it is indexed.
        write(dir, editions());

        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> leaking = files.filter(Files::isRegularFile)
                    .filter(file -> containsAny(file, "sig=", "vote=", "edition=", RECEIVER))
                    .toList();
            assertThat(leaking).isEmpty();
        }
    }

    private static boolean containsAny(Path file, String... needles) {
        try {
            String body = Files.readString(file);
            return Stream.of(needles).anyMatch(body::contains);
        } catch (IOException _) {
            return false;
        }
    }

    private List<ArchivedEdition> editions() {
        return List.of(
                new ArchivedEdition("job-newer", Instant.parse("2026-10-15T04:10:00Z"), fixture.report()),
                new ArchivedEdition("job-older", Instant.parse("2026-10-12T04:10:00Z"), fixture.report()));
    }

    private void write(Path dir, List<ArchivedEdition> editions) throws Exception {
        ArchiveSiteFileAdapter adapter = new ArchiveSiteFileAdapter(
                new PublicEditionRenderer(new WatchlistProperties(true, List.of("Java")),
                        new ThinWeekProperties(true, 3)),
                new ArchiveSiteBuilder(), new AtomFeedBuilder());
        set(adapter, "outputPath", dir.toString());
        set(adapter, "siteUrl", SITE);
        adapter.write(editions);
    }

    private static void set(Object target, String field, String value) throws Exception {
        Field f = ArchiveSiteFileAdapter.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
