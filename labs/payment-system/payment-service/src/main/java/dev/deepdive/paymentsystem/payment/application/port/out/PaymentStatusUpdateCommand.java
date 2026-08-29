package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;

public class PaymentStatusUpdateCommand {

    private final String paymentKey;
    private final String orderId;
    private final PaymentStatus status;
    private final PaymentExtraDetails extraDetails;
    private final PaymentFailure failure;

    public PaymentStatusUpdateCommand(
            String paymentKey,
            String orderId,
            PaymentStatus status,
            PaymentExtraDetails extraDetails,
            PaymentFailure failure
    ) {
        if (status != PaymentStatus.SUCCESS && status != PaymentStatus.FAILURE && status != PaymentStatus.UNKNOWN) {
            throw new IllegalArgumentException("결제 상태 (status: " + status + ") 는 올바르지 않은 결제 상태입니다.");
        }
        if (status == PaymentStatus.SUCCESS && extraDetails == null) {
            throw new IllegalArgumentException("PaymentStatus 값이 SUCCESS 라면 PaymentExtraDetails 는 null 이 되면 안됩니다.");
        }
        if (status == PaymentStatus.FAILURE && failure == null) {
            throw new IllegalArgumentException("PaymentStatus 값이 FAILURE 라면 PaymentFailure 는 null 이 되면 안됩니다.");
        }
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.status = status;
        this.extraDetails = extraDetails;
        this.failure = failure;
    }

    public PaymentStatusUpdateCommand(PaymentExecutionResult paymentExecutionResult) {
        this(
                paymentExecutionResult.paymentKey(),
                paymentExecutionResult.orderId(),
                paymentExecutionResult.paymentStatus(),
                paymentExecutionResult.extraDetails(),
                paymentExecutionResult.failure()
        );
    }

    public String paymentKey() {
        return paymentKey;
    }

    public String orderId() {
        return orderId;
    }

    public PaymentStatus status() {
        return status;
    }

    public PaymentExtraDetails extraDetails() {
        return extraDetails;
    }

    public PaymentFailure failure() {
        return failure;
    }
}
