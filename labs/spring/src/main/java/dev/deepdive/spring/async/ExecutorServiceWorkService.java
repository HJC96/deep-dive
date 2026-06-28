package dev.deepdive.spring.async;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;

@Service
public class ExecutorServiceWorkService {

    private final ExecutorService javaExecutorService;

    public ExecutorServiceWorkService(ExecutorService javaExecutorService) {
        this.javaExecutorService = javaExecutorService;
    }

    public CompletableFuture<WorkResult> doWork(String taskName, Duration delay) {
        return CompletableFuture.supplyAsync(() -> {
            WorkSleeper.sleep(delay);
            return WorkResult.fromCurrentThread(taskName);
        }, javaExecutorService);
    }
}
