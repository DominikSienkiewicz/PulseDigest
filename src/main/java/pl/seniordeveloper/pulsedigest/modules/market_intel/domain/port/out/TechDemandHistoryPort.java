package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.MonthMentions;

import java.util.Optional;

/**
 * Persists one snapshot per monthly "Who is hiring?" thread, so the month-over-month delta stops
 * being a stateless re-scrape of a thousand comments and becomes an accumulating series.
 *
 * <p>Counts are stored against the vocabulary that produced them: changing the technology list
 * changes what "mentions" means, and comparing across that boundary would be a lie.
 */
public interface TechDemandHistoryPort {

    /** The snapshot recorded for this month label under this vocabulary, if any. */
    Optional<MonthMentions> findByMonth(String monthLabel, String vocabularyVersion);

    /** Records (or replaces) the snapshot for this month. */
    void save(MonthMentions month, String vocabularyVersion);
}
