package pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.trend_analytics.domain.model.HistoricalDigest;

import java.util.List;

/**
 * Port odczytu historycznych raportów z N ostatnich dni.
 */
public interface HistoricalDigestPort {

    List<HistoricalDigest> fetchRecent(int lookbackDays);
}
