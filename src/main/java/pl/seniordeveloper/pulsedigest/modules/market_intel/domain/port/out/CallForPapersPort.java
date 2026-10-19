package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CallForPapers;

import java.time.LocalDate;
import java.util.List;

/** Reads conference calls for papers that are still open (N09). */
public interface CallForPapersPort {

    /**
     * @param today  the day deadlines are measured against
     * @return open calls, earliest deadline first; empty when the source is unreachable, because a
     *         missing CFP section is a smaller failure than a wrong one
     */
    List<CallForPapers> openCalls(LocalDate today);
}
