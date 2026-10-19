package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;

/**
 * Port for persisting an edition. Write-only by design.
 *
 * <p>It carried {@code getLatest()} until 2026-10-18, for a module that read editions back through
 * two query services nothing ever called. Reading the archive is not gone from the system — it is
 * {@link ReportHistoryPort}'s job, and that port exists because trend memory, the weekly recap and
 * the cross-edition dedup read *inside* the stored payload rather than fetching whole editions. A
 * second, whole-edition read path served nothing.
 */
public interface ReportStoragePort {
    void save(PersistedReport report);
}
