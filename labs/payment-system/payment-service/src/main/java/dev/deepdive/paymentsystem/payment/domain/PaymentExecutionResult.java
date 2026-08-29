package dev.deepdive.paymentsystem.payment.domain;

public class PaymentExecutionResult {

    private final String paymentKey;
    private final String orderId;
    private final PaymentExtraDetails extraDetails;
    private final PaymentFailure failure;
    private final boolean isSuccess;
    private final boolean isFailure;
    private final boolean isUnknown;
    private final boolean isRetryable;

    public PaymentExecutionResult(
            String paymentKey,
            String orderId,
            PaymentExtraDetails extraDetails,
            PaymentFailure failure,
            boolean isSuccess,
            boolean isFailure,
            boolean isUnknown,
            boolean isRetryable
    ) {
        if (!(isSuccess || isFailure || isUnknown)) {
            throw new IllegalArgumentException("결제 (orderId: " + orderId + ") 는 올바르지 않은 결제 상태입니다.");
        }
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.extraDetails = extraDetails;
        this.failure = failure;
        this.isSuccess = isSuccess;
        this.isFailure = isFailure;
        this.isUnknown = isUnknown;
        this.isRetryable = isRetryable;
    }

    public PaymentStatus paymentStatus() {
        if (isSuccess) {
            return PaymentStatus.SUCCESS;
        }
        if (isFailure) {
            return PaymentStatus.FAILURE;
        }
        if (isUnknown) {
            return PaymentStatus.UNKNOWN;
        }
        throw new IllegalStateException("결제 (orderId: " + orderId + ") 는 올바르지 않은 결제 상태입니다.");
    }

    public String paymentKey() {
        return paymentKey;
    }

    public String orderId() {
        return orderId;
    }

    public PaymentExtraDetails extraDetails() {
        return extraDetails;
    }

    public PaymentFailure failure() {
        return failure;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public boolean isFailure() {
        return isFailure;
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    public boolean isRetryable() {
        return isRetryable;
    }
}
