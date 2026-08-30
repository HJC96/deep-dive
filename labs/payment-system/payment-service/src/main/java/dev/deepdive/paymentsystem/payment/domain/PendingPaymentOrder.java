package dev.deepdive.paymentsystem.payment.domain;

public record PendingPaymentOrder(
        long paymentOrderId,
        PaymentStatus status,
        long amount,
        byte failedCount,
        byte threshold
) {
}
