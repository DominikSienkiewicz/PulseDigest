package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.EditionDeliveryPort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.DeliveryProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * The delivery interlock, held in Postgres because that is the only thing two runs share (F16).
 *
 * <p>An in-process lock would guard nothing: the duplicate this exists to stop is a second
 * <em>process</em>, usually on a second runner. The claim is therefore a conditional insert on the
 * edition date's primary key, which makes the decision the database's rather than the
 * application's — and a race between two conditional inserts on one key has exactly one winner.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SupabaseEditionDeliveryAdapter implements EditionDeliveryPort {

    /**
     * Insert, or take over a claim that was never finished and has gone stale. The
     * {@code delivered_at IS NULL} condition is what makes a delivered edition permanent: no
     * timeout, however long, reopens a send that actually happened.
     */
    private static final String CLAIM_SQL = """
            INSERT INTO edition_deliveries (edition_date, job_id)
            VALUES (?, ?)
            ON CONFLICT (edition_date) DO UPDATE
               SET job_id = EXCLUDED.job_id, claimed_at = now()
             WHERE edition_deliveries.delivered_at IS NULL
               AND edition_deliveries.claimed_at < now() - make_interval(mins => ?)
            """;

    private static final String MARK_DELIVERED_SQL = """
            UPDATE edition_deliveries
               SET delivered_at = now()
             WHERE edition_date = ? AND job_id = ?
            """;

    private static final String DELIVERED_AT_SQL = """
            SELECT delivered_at FROM edition_deliveries
             WHERE edition_date = ? AND delivered_at IS NOT NULL
            """;

    private final JdbcClient jdbcClient;
    private final DeliveryProperties delivery;

    @Override
    public boolean claim(LocalDate edition, String jobId) {
        int claimed = jdbcClient.sql(CLAIM_SQL)
                .params(edition, jobId, delivery.claimTimeoutMinutes())
                .update();
        if (claimed == 0) {
            log.warn("delivery-claim refused edition={} job={} — another run holds it or it already went out",
                    edition, jobId);
            return false;
        }
        log.info("delivery-claim taken edition={} job={}", edition, jobId);
        return true;
    }

    @Override
    public void markDelivered(LocalDate edition, String jobId) {
        int updated = jdbcClient.sql(MARK_DELIVERED_SQL).params(edition, jobId).update();
        if (updated == 0) {
            // The claim was taken over mid-send. Worth a loud line: it means two runs were in flight
            // and the timeout is tuned shorter than a send actually takes.
            log.error("delivery-mark lost its claim edition={} job={} — the edition was sent anyway",
                    edition, jobId);
            return;
        }
        log.info("delivery-mark recorded edition={} job={}", edition, jobId);
    }

    @Override
    public Optional<Instant> deliveredAt(LocalDate edition) {
        return jdbcClient.sql(DELIVERED_AT_SQL)
                .param(edition)
                .query((rs, rowNum) -> rs.getObject("delivered_at", OffsetDateTime.class).toInstant())
                .optional();
    }
}
