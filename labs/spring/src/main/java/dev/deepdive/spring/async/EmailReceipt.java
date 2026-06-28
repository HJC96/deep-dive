package dev.deepdive.spring.async;

public record EmailReceipt(
        String orderId,
        String to,
        String subject,
        String body,
        String runner,
        String threadName
) {
}
