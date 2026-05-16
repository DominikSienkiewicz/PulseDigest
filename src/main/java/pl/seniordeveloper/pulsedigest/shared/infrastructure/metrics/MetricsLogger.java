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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsLogger {

    private final MeterRegistry registry;

    @EventListener
    public void onShutdown(ContextClosedEvent event) {
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
                    String.format("%.1f", t.mean(java.util.concurrent.TimeUnit.MILLISECONDS)),
                    String.format("%.1f", t.max(java.util.concurrent.TimeUnit.MILLISECONDS)));
            default -> log.info("metric other name={} tags={} type={}",
                    name, tags, meter.getId().getType());
        }
    }
}
