package pl.seniordeveloper.pulsedigest.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String REPORT_EXECUTOR = "reportTaskExecutor";
    public static final String DATA_FETCH_EXECUTOR = "dataFetchExecutor";

    @Bean(name = REPORT_EXECUTOR)
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("report-gen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean(name = DATA_FETCH_EXECUTOR)
    public Executor dataFetchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
