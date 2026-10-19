package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ArchiveSitePort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.util.List;

/**
 * Rebuilds the whole public archive from stored editions (N31–N33).
 *
 * <p>A full rebuild rather than appending today's page. Appending needs the previous output to be
 * present and correct, which for a site published from CI means trusting a directory nobody can see;
 * regenerating from the database makes the site a pure function of what is stored, so a bad
 * deployment is fixed by running it again.
 *
 * <p>Every edition goes through {@code PublicEditionRenderer} on the way out. Nothing here decides
 * what is safe to publish — that decision has one home, and it is not this orchestration.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchivePublishService {

    private final ReportHistoryPort reportHistoryPort;
    private final ArchiveSitePort archiveSitePort;

    /** @return how many editions were published; zero means there was nothing stored to publish */
    public int publish(int lookbackDays) {
        List<ArchivedEdition> editions = reportHistoryPort.archivedEditions(lookbackDays);
        if (editions.isEmpty()) {
            log.warn("Archive: no stored editions in the last {}d — nothing to publish", lookbackDays);
            return 0;
        }
        archiveSitePort.write(editions);
        log.info("Archive: published {} edition(s)", editions.size());
        return editions.size();
    }
}
