package dev.deepdive.spring.retry;

public class UnknownPaymentStateException extends PaymentException {

    public UnknownPaymentStateException(String message) {
        super(message);
    }
}
