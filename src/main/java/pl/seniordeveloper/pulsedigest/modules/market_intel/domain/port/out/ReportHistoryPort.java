package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;

import java.util.List;
import java.util.Optional;

/**
 * Reads previously delivered editions back out of report storage.
 *
 * <p>The digest already writes the full scored payload of every edition; this port is what turns
 * that write-only archive into product value — trend memory, "first signal" dates, weekly recap.
 */
public interface ReportHistoryPort {

    /**
     * Editions published within the window, newest first. Never includes the edition currently
     * being assembled — it has not been persisted yet when this is called.
     *
     * @param lookbackDays how far back to read
     */
    List<PastEdition> recentEditions(int lookbackDays);

    /**
     * One past edition by job id, for replaying it (N25).
     *
     * <p>A by-id read belongs here rather than on {@code ReportStoragePort}, which is write-only by
     * design: reading the archive is this port's job, and "replay edition X" is a different
     * capability from the "latest report" query that was removed for having no callers.
     */
    Optional<PastEdition> edition(String jobId);

    /**
     * Whole editions in the window, newest first, for republishing them (N31).
     *
     * <p>{@link #recentEditions} deliberately projects only the signals, because that is all the
     * trend memory and the yield ledger ever needed to read. An archive page needs the editorial and
     * the items too — which the payload has held all along.
     */
    List<ArchivedEdition> archivedEditions(int lookbackDays);
}
