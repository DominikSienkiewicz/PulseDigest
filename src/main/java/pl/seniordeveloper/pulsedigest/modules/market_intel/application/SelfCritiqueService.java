package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SelfCritiquePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.CritiqueVerdict;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.SelfCritiquePort;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the self-critique and decides what, if anything, it is allowed to do (N23).
 *
 * <p>Ships in shadow mode: the verdict is logged in full, item by item with the reason, and the
 * edition goes out unchanged. Six editions of that log are what the decision to enforce should be
 * made on — and because the log names URLs and reasons, that decision can be made by reading rather
 * than by trusting.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SelfCritiqueService {

    /**
     * A critique that wants to cut more than this is not critiquing, it is disagreeing with the
     * whole edition — far more likely a confused model than a uniformly bad digest. Ignored outright
     * rather than partially applied, because picking which of its cuts to honour would be inventing
     * a judgement neither the model nor the rubric made.
     */
    private static final double MAX_CUT_SHARE = 0.5;

    private final SelfCritiquePort selfCritiquePort;
    private final SelfCritiquePolicy policy;

    /**
     * @return the items that survive — identical to the input unless the policy is enforcing
     */
    public List<DigestItem> apply(List<DigestItem> items) {
        if (!policy.enabled() || items == null || items.isEmpty()) {
            return items;
        }
        CritiqueVerdict verdict = selfCritiquePort.critique(items);
        if (verdict.cuts().isEmpty()) {
            log.info("self-critique mode={} cuts=0", policy.enforcing() ? "enforcing" : "shadow");
            return items;
        }

        verdict.cuts().forEach(cut -> log.info("self-critique would-cut url={} reason={}",
                cut.url(), cut.reason()));

        if (verdict.cuts().size() > items.size() * MAX_CUT_SHARE) {
            log.warn("self-critique rejected: it would cut {} of {} items, which is disagreement "
                    + "with the edition rather than critique of it", verdict.cuts().size(), items.size());
            return items;
        }
        if (!policy.enforcing()) {
            log.info("self-critique mode=shadow cuts={} — recorded, not applied", verdict.cuts().size());
            return items;
        }

        Set<String> dropped = verdict.cuts().stream()
                .map(CritiqueVerdict.Cut::url)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());
        List<DigestItem> kept = items.stream().filter(item -> !dropped.contains(item.url())).toList();
        log.info("self-critique mode=enforcing cuts={} kept={}", items.size() - kept.size(), kept.size());
        return kept;
    }
}
