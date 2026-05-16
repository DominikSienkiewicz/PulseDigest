package pl.seniordeveloper.pulsedigest.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private SimpleMeterRegistry registry;

    @Bean
    SimpleMeterRegistry simpleMeterRegistry() {
        this.registry = new SimpleMeterRegistry();
        return registry;
    }

    @PostConstruct
    void registerGlobally() {
        Metrics.addRegistry(registry);
    }

    @PreDestroy
    void unregister() {
        Metrics.removeRegistry(registry);
    }
}
