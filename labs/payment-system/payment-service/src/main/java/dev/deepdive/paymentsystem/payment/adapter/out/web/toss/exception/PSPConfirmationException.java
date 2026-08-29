package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception;

import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;

public class PSPConfirmationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final boolean isSuccess;
    private final boolean isFailure;
    private final boolean isUnknown;
    private final boolean isRetryableError;

    public PSPConfirmationException(
            String errorCode,
            String errorMessage,
            boolean isSuccess,
            boolean isFailure,
            boolean isUnknown,
            boolean isRetryableError
    ) {
        this(errorCode, errorMessage, isSuccess, isFailure, isUnknown, isRetryableError, null);
    }

    public PSPConfirmationException(
            String errorCode,
            String errorMessage,
            boolean isSuccess,
            boolean isFailure,
            boolean isUnknown,
            boolean isRetryableError,
            Throwable cause
    ) {
        super(errorMessage, cause);
        if (!(isSuccess || isFailure || isUnknown)) {
            throw new IllegalArgumentException("PSPConfirmationException 는 올바르지 않은 결제 상태를 가지고 있습니다.");
        }
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.isSuccess = isSuccess;
        this.isFailure = isFailure;
        this.isUnknown = isUnknown;
        this.isRetryableError = isRetryableError;
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
        throw new IllegalStateException("PSPConfirmationException 는 올바르지 않은 결제 상태를 가지고 있습니다.");
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
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

    public boolean isRetryableError() {
        return isRetryableError;
    }
}
