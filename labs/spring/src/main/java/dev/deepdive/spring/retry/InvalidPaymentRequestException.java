package dev.deepdive.spring.retry;

public class InvalidPaymentRequestException extends PaymentException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
