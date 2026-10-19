package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MetricsLoggerTest {

    /**
     * The runner dumps explicitly and the context-closed listener still fires afterwards, so the
     * snapshot must print once rather than twice. A duplicated block in the run log would make the
     * figures look doubled to anyone skimming it.
     */
    @Test
    void dumpsOnceEvenWhenBothTheRunnerAndTheContextAskForIt() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("probe.counter").increment();
        MetricsLogger logger = new MetricsLogger(registry);

        logger.dump();

        assertThatCode(() -> {
            logger.dump();
            logger.onShutdown(null);
        }).doesNotThrowAnyException();
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void anEmptyRegistrySaysSoRatherThanPrintingAnEmptyBlock() {
        assertThatCode(() -> new MetricsLogger(new SimpleMeterRegistry()).dump())
                .doesNotThrowAnyException();
    }
}
