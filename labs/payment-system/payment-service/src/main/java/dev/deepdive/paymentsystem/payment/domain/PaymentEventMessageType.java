package dev.deepdive.paymentsystem.payment.domain;

public enum PaymentEventMessageType {
    PAYMENT_CONFIRMATION_SUCCESS("결제 승인 완료 이벤트");

    PaymentEventMessageType(String description) {
    }
}
