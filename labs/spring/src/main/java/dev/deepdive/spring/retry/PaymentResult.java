package dev.deepdive.spring.retry;

public record PaymentResult(String orderId, Status status) {

    public static PaymentResult completed(String orderId) {
        return new PaymentResult(orderId, Status.COMPLETED);
    }

    public static PaymentResult rejected(String orderId) {
        return new PaymentResult(orderId, Status.REJECTED);
    }

    public static PaymentResult reviewRequired(String orderId) {
        return new PaymentResult(orderId, Status.REVIEW_REQUIRED);
    }

    public enum Status {
        COMPLETED,
        REJECTED,
        REVIEW_REQUIRED
    }
}
