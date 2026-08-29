package dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception;

public class PaymentValidationException extends RuntimeException {

    public PaymentValidationException(String message) {
        super(message);
    }
}
