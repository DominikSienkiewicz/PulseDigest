package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.seniordeveloper.pulsedigest.shared.async.AsyncQualifiers;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Executor configuration for the batch pipeline.
 *
 * <p>The report pipeline itself runs synchronously on the application-runner thread (see
 * {@code DigestRunner}); the only executor needed is the virtual-thread pool that fans out the
 * parallel source fetches.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = AsyncQualifiers.DATA_FETCH_EXECUTOR)
    public Executor dataFetchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
