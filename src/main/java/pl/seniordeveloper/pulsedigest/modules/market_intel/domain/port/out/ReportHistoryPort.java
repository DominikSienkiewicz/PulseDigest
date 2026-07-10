package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;

import java.util.List;

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
}
