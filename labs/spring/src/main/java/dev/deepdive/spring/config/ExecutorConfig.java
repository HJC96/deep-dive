package dev.deepdive.spring.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor springAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("spring-async-");
        executor.initialize();
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService javaExecutorService() {
        return Executors.newFixedThreadPool(2, namedThreadFactory("java-executor-"));
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + sequence.incrementAndGet());
            return thread;
        };
    }
}
