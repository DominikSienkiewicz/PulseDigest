package pl.seniordeveloper.pulsedigest.domain;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportHistoryPort;

import java.util.List;
import java.util.Optional;

/** A history port backed by a fixed list of editions. */
public record TestHistory(List<PastEdition> editions) implements ReportHistoryPort {

    @Override
    public List<PastEdition> recentEditions(int lookbackDays) {
        return editions;
    }

    @Override
    public Optional<PastEdition> edition(String jobId) {
        return Optional.empty();
    }

    @Override
    public List<pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition>
            archivedEditions(int lookbackDays) {
        return List.of();
    }
}
