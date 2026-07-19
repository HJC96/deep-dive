package dev.deepdive.spring.retry;

public class TemporaryPaymentException extends PaymentException {

    public TemporaryPaymentException(String message) {
        super(message);
    }
}
