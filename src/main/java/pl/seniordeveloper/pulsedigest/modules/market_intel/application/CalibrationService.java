package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Calibration;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SignalRank;

import java.util.List;
import java.util.Set;

/**
 * Measures whether the reader agrees with the 🔴 badge (N12).
 *
 * <p>Counts past editions' CRITICAL stories and how many of them the reader up-voted. Deliberately
 * computed over *past* editions only: this edition's own 🔴 items have had no chance to be voted on,
 * and including them would drag every hit rate towards zero by construction.
 */
@Slf4j
@Service
public class CalibrationService {

    public Calibration measure(List<PastEdition> editions, Set<String> upvotedUrls) {
        if (editions == null || editions.isEmpty()) {
            return new Calibration(0, 0);
        }
        List<String> criticalUrls = editions.stream()
                .flatMap(edition -> edition.signals().stream())
                .filter(signal -> signal.rank() == SignalRank.CRITICAL)
                .map(signal -> signal.item().url())
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        Set<String> upvoted = upvotedUrls != null ? upvotedUrls : Set.of();
        int agreed = (int) criticalUrls.stream().filter(upvoted::contains).count();

        Calibration calibration = new Calibration(criticalUrls.size(), agreed);
        log.info("calibration critical={} upvoted={} reportable={}",
                calibration.criticalItems(), calibration.upvoted(), calibration.isReportable());
        return calibration;
    }
}
