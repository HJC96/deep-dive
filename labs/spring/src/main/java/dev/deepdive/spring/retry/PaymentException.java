package dev.deepdive.spring.retry;

public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
