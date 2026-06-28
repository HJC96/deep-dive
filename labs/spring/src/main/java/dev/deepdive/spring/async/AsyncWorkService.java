package dev.deepdive.spring.async;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncWorkService {

    @Async("springAsyncExecutor")
    public CompletableFuture<WorkResult> doWork(String taskName, Duration delay) {
        WorkSleeper.sleep(delay);
        return CompletableFuture.completedFuture(WorkResult.fromCurrentThread(taskName));
    }

    public CompletableFuture<WorkResult> callAsyncMethodFromSameBean(String taskName) {
        return doWork(taskName, Duration.ZERO);
    }
}
