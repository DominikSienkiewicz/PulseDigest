package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.archive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ArchiveSitePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the archive to a directory for a static host to pick up (N31–N33).
 *
 * <p>Plain files, no deployment logic: the workflow uploads whatever is here. That keeps the one
 * irreversible step — making editions public — a single explicit action in CI rather than something
 * a library does on the application's behalf.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveSiteFileAdapter implements ArchiveSitePort {

    private final PublicEditionRenderer publicEditionRenderer;
    private final ArchiveSiteBuilder siteBuilder;
    private final AtomFeedBuilder feedBuilder;

    @Value("${report.archive.output-path:build/archive}")
    private String outputPath;

    @Value("${report.archive.site-url:https://dominiksienkiewicz.github.io/PulseDigest/}")
    private String siteUrl;

    @Override
    public void write(List<ArchivedEdition> editions) {
        Path root = Path.of(outputPath);
        try {
            Files.createDirectories(root);
            for (ArchivedEdition edition : editions) {
                // Research is not stored with the edition, so the public page renders without it —
                // which is why the intake footer line is one of the sections the renderer disables.
                String html = publicEditionRenderer.render(edition.report(), null);
                write(root.resolve(ArchiveSiteBuilder.slug(edition) + ".html"),
                        siteBuilder.buildEditionPage(edition, html));
            }
            write(root.resolve("index.html"), siteBuilder.buildIndex(editions));
            write(root.resolve("search.html"), siteBuilder.buildSearchPage());
            write(root.resolve("search-index.json"), siteBuilder.buildSearchIndex(editions));
            write(root.resolve("feed.xml"), feedBuilder.build(editions, siteUrl));
            // Pages would otherwise run the output through Jekyll, which eats files whose names
            // start with an underscore and adds a build step nothing here needs.
            write(root.resolve(".nojekyll"), "");
            log.info("Archive written to {} ({} edition page(s) + index, search, feed)",
                    root.toAbsolutePath(), editions.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the archive to " + root, e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
