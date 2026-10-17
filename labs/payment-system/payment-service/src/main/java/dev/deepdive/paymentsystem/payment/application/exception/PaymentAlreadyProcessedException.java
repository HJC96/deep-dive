package dev.deepdive.paymentsystem.payment.application.exception;

import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;

/**
 * 이미 최종 상태(SUCCESS/FAILURE)이거나 다른 요청이 승인 중(EXECUTING)인 결제에 다시 손대려 할 때 던진다.
 * 상태를 바꾸지 않고 현재 상태를 그대로 돌려주는 no-op 으로 처리한다.
 */
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
