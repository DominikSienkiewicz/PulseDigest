package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The one edition-per-day interlock (F16).
 *
 * <p>Until this existed, nothing in the application stopped a second run from sending the same
 * edition again — the protection was a GitHub Actions {@code concurrency} group, which guards the
 * one path that goes through the orchestrator and nothing else. A manual JAR run, a second runner,
 * or a rewritten workflow would deliver the digest twice and pay OpenAI twice.
 */
public interface EditionDeliveryPort {

    /**
     * Attempts to become the run that sends {@code edition}.
     *
     * <p>Race-free by construction: the claim is a conditional insert on a primary key, so of two
     * runs reaching it at the same moment exactly one is told yes. An unfinished claim left by a run
     * that died can be taken over after a timeout — a crashed run must not brick the edition — but a
     * claim that reached delivery is never taken over by anything.
     *
     * @return {@code true} when this run owns the send
     */
    boolean claim(LocalDate edition, String jobId);

    /** Records that the claimed edition actually went out. */
    void markDelivered(LocalDate edition, String jobId);

    /**
     * When {@code edition} was delivered, if it was. Used for the cheap check at the top of the
     * pipeline: advisory rather than authoritative — {@link #claim} is what decides — but it is
     * what stops a duplicate run before it spends money rather than after.
     */
    Optional<Instant> deliveredAt(LocalDate edition);
}
