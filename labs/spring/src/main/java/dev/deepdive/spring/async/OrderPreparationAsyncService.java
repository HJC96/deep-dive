package dev.deepdive.spring.async;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderPreparationAsyncService {

    @Async("springAsyncExecutor")
    public CompletableFuture<String> confirmPayment(Order order) {
        pause(Duration.ofMillis(250));
        return CompletableFuture.completedFuture("결제 승인");
    }

    @Async("springAsyncExecutor")
    public CompletableFuture<String> checkInventory(Order order) {
        pause(Duration.ofMillis(200));
        return CompletableFuture.completedFuture("재고 확보");
    }

    @Async("springAsyncExecutor")
    public CompletableFuture<String> estimateDelivery(Order order) {
        pause(Duration.ofMillis(220));
        return CompletableFuture.completedFuture("2일 후 도착");
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
