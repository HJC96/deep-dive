package dev.deepdive.paymentsystem.payment.application.port.out;

import reactor.core.publisher.Mono;

public interface PaymentStatusUpdatePort {

    /**
     * 승인 요청(confirm)이 결제를 EXECUTING 으로 선점한다. NOT_STARTED·UNKNOWN 에서만 넘어간다.
     * 이미 EXECUTING·SUCCESS·FAILURE 면 {@code PaymentAlreadyProcessedException}, 주문이 없으면 {@code PaymentNotFoundException}.
     */
    Mono<Boolean> updatePaymentStatusToExecuting(String orderId, String paymentKey);

    /**
     * 복구 스케줄러가 결제를 EXECUTING 으로 선점한다. UNKNOWN 이거나 3분 넘게 EXECUTING 에 머문 결제만 넘어간다.
     * 선점에 실패하면(다른 요청이 먼저 가져갔거나 이미 끝났으면) false.
     */
    Mono<Boolean> updatePaymentStatusToExecutingForRecovery(String orderId);

    /**
     * 최종 상태(SUCCESS/FAILURE/UNKNOWN)를 기록한다. EXECUTING·UNKNOWN 에서만 넘어가며,
     * 이미 SUCCESS·FAILURE 면 덮어쓰지 않고 {@code PaymentAlreadyProcessedException} 을 낸다.
     */
    Mono<Boolean> updatePaymentStatus(PaymentStatusUpdateCommand command);
}
