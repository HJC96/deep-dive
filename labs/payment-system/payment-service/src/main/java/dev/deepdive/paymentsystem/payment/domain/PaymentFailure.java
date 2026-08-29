package dev.deepdive.paymentsystem.payment.domain;

public record PaymentFailure(
        String errorCode,
        String message
) {
}
