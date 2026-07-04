package dev.deepdive.spring.async;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncWorkService {

    private final ExecutorService javaExecutorService;
    private final OrderConfirmationEmailSender emailSender;
    private final OrderPreparationAsyncService orderPreparationAsyncService;

    public AsyncWorkService(
            ExecutorService javaExecutorService,
            OrderConfirmationEmailSender emailSender,
            OrderPreparationAsyncService orderPreparationAsyncService
    ) {
        this.javaExecutorService = javaExecutorService;
        this.emailSender = emailSender;
        this.orderPreparationAsyncService = orderPreparationAsyncService;
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

    public EmailReceipt prepareOrderConfirmationEmailSequentially(Order order) {
        String paymentStatus = confirmPayment(order);
        String inventoryStatus = checkInventory(order);
        String deliveryEstimate = estimateDelivery(order);

        return emailSender.sendWithPreparation(
                order,
                "CompletableFuture",
                paymentStatus,
                inventoryStatus,
                deliveryEstimate
        );
    }

    public EmailReceipt prepareOrderConfirmationEmailInParallel(Order order) {
        CompletableFuture<String> paymentStatus = CompletableFuture.supplyAsync(
                () -> confirmPayment(order),
                javaExecutorService
        );
        CompletableFuture<String> inventoryStatus = CompletableFuture.supplyAsync(
                () -> checkInventory(order),
                javaExecutorService
        );
        CompletableFuture<String> deliveryEstimate = CompletableFuture.supplyAsync(
                () -> estimateDelivery(order),
                javaExecutorService
        );

        return emailSender.sendWithPreparation(
                order,
                "CompletableFuture",
                paymentStatus.join(),
                inventoryStatus.join(),
                deliveryEstimate.join()
        );
    }

    public EmailReceipt prepareOrderConfirmationEmailWithAsyncSequentially(Order order) {
        String paymentStatus = orderPreparationAsyncService.confirmPayment(order).join();
        String inventoryStatus = orderPreparationAsyncService.checkInventory(order).join();
        String deliveryEstimate = orderPreparationAsyncService.estimateDelivery(order).join();

        return emailSender.sendWithPreparation(
                order,
                "@Async",
                paymentStatus,
                inventoryStatus,
                deliveryEstimate
        );
    }

    public EmailReceipt prepareOrderConfirmationEmailWithAsyncInParallel(Order order) {
        CompletableFuture<String> paymentStatus = orderPreparationAsyncService.confirmPayment(order);
        CompletableFuture<String> inventoryStatus = orderPreparationAsyncService.checkInventory(order);
        CompletableFuture<String> deliveryEstimate = orderPreparationAsyncService.estimateDelivery(order);

        return emailSender.sendWithPreparation(
                order,
                "@Async",
                paymentStatus.join(),
                inventoryStatus.join(),
                deliveryEstimate.join()
        );
    }

    private String confirmPayment(Order order) {
        pause(Duration.ofMillis(250));
        return "결제 승인";
    }

    private String checkInventory(Order order) {
        pause(Duration.ofMillis(200));
        return "재고 확보";
    }

    private String estimateDelivery(Order order) {
        pause(Duration.ofMillis(220));
        return "2일 후 도착";
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
