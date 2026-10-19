package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.EolPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.EolNotice;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Selects the end-of-life dates worth a line in this edition (N08).
 *
 * <p>Past dates first, then the nearest ahead: something already unsupported is a different message
 * from something that will be, and burying the first under the second would be the wrong order.
 * Everything beyond the warning window is silence rather than a list — a radar that prints every
 * date it knows is a table, not a radar.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EolRadarService {

    private final EolPolicy policy;

    public List<EolNotice> due(LocalDate today) {
        if (!policy.enabled() || policy.entries().isEmpty()) {
            return List.of();
        }
        List<EolNotice> due = policy.entries().stream()
                .filter(notice -> notice.isPast(today)
                        || notice.monthsUntil(today) <= policy.warnWithinMonths())
                .sorted(Comparator.comparing(EolNotice::endOfLife))
                .toList();
        log.info("eol-radar known={} due={} table_valid_as_of={} warn_within_months={}",
                policy.entries().size(), due.size(), policy.validAsOf(), policy.warnWithinMonths());
        return due;
    }
}
