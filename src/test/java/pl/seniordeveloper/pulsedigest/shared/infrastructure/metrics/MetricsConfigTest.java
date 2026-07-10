package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MetricsConfigTest {

    /**
     * Reproduces the production failure "Cannot invoke MeterRegistry.counter(Meter.Id) because
     * registry is null": {@code MetricsConfig} used to register its registry from a {@code @PostConstruct}
     * that read a field set only by the {@code @Bean} method. Spring runs the config bean's
     * {@code @PostConstruct} before invoking its {@code @Bean} method, so the field was still {@code null}
     * and a {@code null} child was pushed into the global composite — every later {@code Metrics.counter}
     * then dereferenced it.
     */
    @Test
    void registersCreatedRegistryGloballySoGlobalCountersMaterialize() {
        Set<MeterRegistry> before = new HashSet<>(Metrics.globalRegistry.getRegistries());
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(MetricsConfig.class)
                    .run(context -> {
                        SimpleMeterRegistry bean = context.getBean(SimpleMeterRegistry.class);
                        assertThat(Metrics.globalRegistry.getRegistries())
                                .as("global composite must contain the registry MetricsConfig created")
                                .contains(bean);
                        assertThatCode(() -> Metrics.counter("test.metricsconfig.smoke").increment())
                                .doesNotThrowAnyException();
                    });
        } finally {
            Metrics.globalRegistry.getRegistries().stream()
                    .filter(r -> !before.contains(r))
                    .toList()
                    .forEach(Metrics::removeRegistry);
        }
    }
}
