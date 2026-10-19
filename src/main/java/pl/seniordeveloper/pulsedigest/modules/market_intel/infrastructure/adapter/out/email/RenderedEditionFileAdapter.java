package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionArchivePort;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the rendered edition next to the run, using the very same builder that produces the email
 * body — so the artifact is the edition that was sent, not a second rendering that could drift.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RenderedEditionFileAdapter implements EditionArchivePort {

    private final ReportEmailBuilder emailBuilder;

    @Value("${report.rendered-edition-path:build/digest.html}")
    private String outputPath;

    @Override
    public void archive(ReportData report, ResearchResult research) {
        try {
            Path target = Path.of(outputPath);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, emailBuilder.buildHtml(report, research), StandardCharsets.UTF_8);
            log.info("Rendered edition written to {}", target.toAbsolutePath());
        } catch (Exception e) {
            // Best-effort by contract: the digest itself succeeded, and losing a local copy of it is
            // not worth failing the run over. The workflow's artifact step simply finds no file.
            log.warn("Could not write the rendered edition to {}: {}", outputPath, e.getMessage());
        }
    }
}
