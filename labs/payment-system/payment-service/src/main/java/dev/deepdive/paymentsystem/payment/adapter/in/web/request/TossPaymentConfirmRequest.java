package dev.deepdive.paymentsystem.payment.adapter.in.web.request;

public record TossPaymentConfirmRequest(
        String paymentKey,
        String orderId,
        String amount
) {
}
