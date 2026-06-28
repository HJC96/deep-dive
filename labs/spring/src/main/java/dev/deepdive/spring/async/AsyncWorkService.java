package dev.deepdive.spring.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncWorkService {

    private final ExecutorService javaExecutorService;
    private final OrderConfirmationEmailSender emailSender;

    public AsyncWorkService(ExecutorService javaExecutorService, OrderConfirmationEmailSender emailSender) {
        this.javaExecutorService = javaExecutorService;
        this.emailSender = emailSender;
    }

    @Async("springAsyncExecutor")
    public CompletableFuture<EmailReceipt> sendOrderConfirmationEmailWithAsync(Order order) {
        return CompletableFuture.completedFuture(emailSender.send(order, "@Async"));
    }

    public CompletableFuture<EmailReceipt> sendOrderConfirmationEmailWithExecutorService(Order order) {
        return CompletableFuture.supplyAsync(
                () -> emailSender.send(order, "ExecutorService"),
                javaExecutorService
        );
    }

    public CompletableFuture<EmailReceipt> callAsyncMethodFromSameBean(Order order) {
        return sendOrderConfirmationEmailWithAsync(order);
    }
}
