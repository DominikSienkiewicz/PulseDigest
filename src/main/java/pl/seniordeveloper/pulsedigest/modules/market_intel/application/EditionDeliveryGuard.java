package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.DeliveryGuardPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;

import java.time.LocalDate;

/**
 * F16: an edition goes out once. The three steps of that promise fail in deliberately different
 * directions, which is the whole reason they belong together rather than scattered through the
 * pipeline that calls them.
 *
 * <p>{@link #alreadyDelivered} fails <em>open</em>. It is the cheap advisory check, run before a
 * single source is fetched or a token spent, and its job is to stop the common duplicate — a manual
 * dispatch after a successful scheduled run. A database that cannot answer must not become the
 * reason today's edition never happens, so an unreadable guard means "go ahead".
 *
 * <p>{@link #claim} fails <em>closed</em>. It is the last gate before an irreversible action, and
 * sending an edition the guard could not reason about is precisely what F16 exists to prevent. Being
 * a conditional insert on the edition date, it also settles the race the advisory check cannot: of
 * two runs arriving together, exactly one is told yes.
 *
 * <p>{@link #markDelivered} cannot fail the run at all. By the time it runs the mail has gone out,
 * and nothing here can unsend it — so the only honest response to a write failure is a loud log.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EditionDeliveryGuard {

    private final DeliveryGuardPolicy deliveryGuardPolicy;
    private final EditionDeliveryPort editionDeliveryPort;

    /** Whether this edition already went out. A storage failure degrades to "not delivered". */
    public boolean alreadyDelivered(String jobId, LocalDate edition) {
        if (!deliveryGuardPolicy.enabled()) {
            return false;
        }
        try {
            return editionDeliveryPort.deliveredAt(edition)
                    .map(at -> {
                        log.error("[{}] Edition {} was already delivered at {} — stopping before the run costs"
                                + " anything", jobId, edition, at);
                        return true;
                    })
                    .orElse(false);
        } catch (Exception e) {
            log.warn("[{}] Delivery guard could not read edition {}: {}", jobId, edition, e.getMessage());
            return false;
        }
    }

    /** Claims the send. A storage failure here refuses the send rather than risking a second one. */
    public boolean claim(String jobId, LocalDate edition) {
        if (!deliveryGuardPolicy.enabled()) {
            return true;
        }
        try {
            return editionDeliveryPort.claim(edition, jobId);
        } catch (Exception e) {
            log.error("[{}] Delivery claim failed for edition {}: {}", jobId, edition, e.getMessage());
            return false;
        }
    }

    /** Records the send. Never fails the run — the email has already gone out, and it cannot be unsent. */
    public void markDelivered(String jobId, LocalDate edition) {
        if (!deliveryGuardPolicy.enabled()) {
            return;
        }
        try {
            editionDeliveryPort.markDelivered(edition, jobId);
        } catch (Exception e) {
            log.error("[{}] Edition {} was delivered but could not be recorded: {}",
                    jobId, edition, e.getMessage());
        }
    }
}
