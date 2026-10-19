package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Dumps registered Micrometer meters to the log on shutdown.
 *
 * <p>The batch process has no HTTP server, so there is nothing for Prometheus to scrape.
 * Logging the final snapshot once, in a structured line per meter, is the cheapest way to
 * surface fetch latencies, retry counts, and LLM usage in CI run output.
 *
 * <p><b>Why {@link #dump()} is public and called explicitly (N43).</b> This used to rely solely on
 * {@code ContextClosedEvent}, which in production arrives from a JVM shutdown hook: {@code
 * DigestRunner} calls {@code System.exit}, the JVM runs its hooks, and Spring's hook closes the
 * context. Shutdown hooks run concurrently in unspecified order, and the logging system has one of
 * its own — so whether these lines reached the log was a race nobody had settled. Everything in
 * F11's cost metering, N35's run summary and N40's budget is read off this snapshot, which made the
 * whole observability story contingent on winning that race.
 *
 * <p>{@code DigestRunner} now dumps explicitly before exiting, while logging is unquestionably
 * alive. The event listener stays for every path that closes a context without exiting — tests, and
 * any future caller — and {@code dump()} is idempotent so the two cannot double-print.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsLogger {

    private final MeterRegistry registry;
    private final java.util.concurrent.atomic.AtomicBoolean dumped =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @EventListener
    public void onShutdown(ContextClosedEvent event) {
        dump();
    }

    /**
     * Writes the snapshot, once. Safe to call from the runner and from a context-closed event: the
     * second call is a no-op rather than a duplicate block in the run log.
     */
    public void dump() {
        if (!dumped.compareAndSet(false, true)) {
            return;
        }
        var meters = registry.getMeters();
        if (meters.isEmpty()) {
            log.info("Metrics: no meters registered");
            return;
        }
        log.info("=== Metrics snapshot ({} meters) ===", meters.size());
        meters.stream()
                .sorted(Comparator.comparing(m -> m.getId().getName()))
                .forEach(this::logMeter);
        log.info("=== End metrics snapshot ===");
    }

    private void logMeter(Meter meter) {
        String name = meter.getId().getName();
        String tags = meter.getId().getTagsAsIterable().toString();
        switch (meter) {
            case Counter c -> log.info("metric counter name={} tags={} count={}",
                    name, tags, (long) c.count());
            case Timer t -> log.info("metric timer name={} tags={} count={} mean_ms={} max_ms={}",
                    name, tags, t.count(),
                    String.format(java.util.Locale.ROOT, "%.1f", t.mean(java.util.concurrent.TimeUnit.MILLISECONDS)),
                    String.format(java.util.Locale.ROOT, "%.1f", t.max(java.util.concurrent.TimeUnit.MILLISECONDS)));
            default -> log.info("metric other name={} tags={} type={}",
                    name, tags, meter.getId().getType());
        }
    }
}
