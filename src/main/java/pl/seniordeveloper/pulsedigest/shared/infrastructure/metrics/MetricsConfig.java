package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private SimpleMeterRegistry registry;

    /**
     * Registers the registry into the global composite here, inside the {@code @Bean} method, rather
     * than from a separate {@code @PostConstruct}. Spring runs a configuration bean's
     * {@code @PostConstruct} before invoking its {@code @Bean} methods, so reading the field there saw
     * a {@code null} registry and pushed {@code null} into {@link Metrics#globalRegistry} — every later
     * {@code Metrics.counter(...)} then dereferenced the null child and threw.
     */
    @Bean
    SimpleMeterRegistry simpleMeterRegistry() {
        this.registry = new SimpleMeterRegistry();
        Metrics.addRegistry(this.registry);
        return this.registry;
    }

    @PreDestroy
    void unregister() {
        if (registry != null) {
            Metrics.removeRegistry(registry);
        }
    }
}
