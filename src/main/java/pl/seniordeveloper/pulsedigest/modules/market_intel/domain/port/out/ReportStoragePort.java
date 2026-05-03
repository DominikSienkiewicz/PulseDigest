package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;

import java.util.Optional;

/**
 * Port for persisting and retrieving reports.
 */
public interface ReportStoragePort {
    void save(PersistedReport report);

    Optional<PersistedReport> getLatest();
}
