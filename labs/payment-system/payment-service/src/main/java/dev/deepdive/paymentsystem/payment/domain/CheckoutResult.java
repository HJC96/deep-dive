package dev.deepdive.paymentsystem.payment.domain;

public record CheckoutResult(
        long amount,
        String orderId,
        String orderName
) {
}
