package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;

import java.io.InputStream;

/**
 * Renders the committed sample edition with no credentials and no network (N37, F12's half).
 *
 * <p>The finding this answers is that cloning the repository ends at "it compiles": four accounts,
 * a dozen environment variables and one paid tier stand between a visitor and seeing the thing work.
 * A dry run puts the real rendering chain — layout, every section builder, both MIME parts — over a
 * real stored edition and writes the result to disk.
 *
 * <p>It reads {@code samples/edition.json}, which is the same file the golden-file test renders. One
 * file with two readers rather than two that drift: a demo showing something the product no longer
 * does would be worse than no demo.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DryRunService {

    private static final String SAMPLE = "/samples/edition.json";

    private final ObjectMapper objectMapper;
    private final EditionArchivePort editionArchivePort;

    /** @return true when the sample rendered and was written; false when it could not be read */
    public boolean render() {
        try (InputStream sample = DryRunService.class.getResourceAsStream(SAMPLE)) {
            if (sample == null) {
                log.error("Sample edition {} is not on the classpath", SAMPLE);
                return false;
            }
            JsonNode root = objectMapper.readTree(sample);
            ReportData report = SampleEditionLoader.report(objectMapper, root);
            ResearchResult research = SampleEditionLoader.research(objectMapper, root);

            log.info("Dry run: {} items, {} signals, no network, no credentials",
                    report.items().size(), report.signals().size());
            editionArchivePort.archive(report, research);
            return true;
        } catch (Exception e) {
            log.error("Dry run failed to render the sample edition: {}", e.getMessage());
            return false;
        }
    }
}
