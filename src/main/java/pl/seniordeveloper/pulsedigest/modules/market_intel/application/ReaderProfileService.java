package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.ReaderProfilePolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileEvidence;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfileDistillerPort;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Keeps the living reader model fresh without letting it drift.
 *
 * <p>Three gates, in order. Nothing is distilled until the reader has clicked {@code minVotes} times —
 * a profile built from three clicks is a hallucination with a database row. Nothing is re-distilled
 * more often than {@code refreshDays}, so this costs one gpt-4o-mini call a week rather than one per
 * run. And whatever is returned has been pruned of hypotheses older than {@code hypothesisTtlDays},
 * because a claim the reader stopped confirming should stop steering the digest.
 *
 * <p>Every failure — storage down, distiller down — degrades to "no profile this run" and leaves the
 * stored one untouched.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReaderProfileService {

    private final ReaderProfilePort profilePort;
    private final ReaderProfileDistillerPort distillerPort;
    private final ReaderProfilePolicy policy;

    /**
     * Re-distils the profile when it is due, then returns the active (TTL-pruned) profile to inject
     * into the prompt and show in the footer.
     */
    public Optional<ReaderProfile> refresh(LocalDate today, ProfileEvidence evidence) {
        if (!policy.enabled()) {
            return Optional.empty();
        }
        try {
            Optional<ReaderProfile> stored = profilePort.latest();
            Optional<ReaderProfile> current = redistilIfDue(today, evidence, stored).or(() -> stored);
            return current.map(profile -> profile.activeOn(today, policy.hypothesisTtlDays()));
        } catch (Exception e) {
            log.warn("Reader profile unavailable — publishing without it: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Empty when the reader has not voted enough, or when the stored profile is still fresh. */
    private Optional<ReaderProfile> redistilIfDue(LocalDate today, ProfileEvidence evidence,
                                                  Optional<ReaderProfile> stored) {
        if (evidence.totalVotes() < policy.minVotes()) {
            log.debug("Reader profile: {} vote(s) is below the {}-vote floor — not distilling",
                    evidence.totalVotes(), policy.minVotes());
            return Optional.empty();
        }
        if (stored.isPresent() && !stored.get().isStaleOn(today, policy.refreshDays())) {
            return Optional.empty();
        }
        Optional<ReaderProfile> distilled = distillerPort.distil(evidence);
        distilled.ifPresent(profile -> {
            profilePort.save(profile);
            log.info("Reader profile re-distilled from {} vote(s): {} hypothesis/es",
                    evidence.totalVotes(), profile.hypotheses().size());
        });
        return distilled;
    }
}
