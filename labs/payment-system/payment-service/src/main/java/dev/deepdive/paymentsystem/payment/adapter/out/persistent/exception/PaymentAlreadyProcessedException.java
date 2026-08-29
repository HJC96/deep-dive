package dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception;

import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;

public class PaymentAlreadyProcessedException extends RuntimeException {

    private final PaymentStatus status;

    public PaymentAlreadyProcessedException(String message, PaymentStatus status) {
        super(message);
        this.status = status;
    }

    public PaymentStatus status() {
        return status;
    }
}
