package dev.deepdive.paymentsystem.payment.application.port.in;

import reactor.core.publisher.Mono;

public interface PaymentRecoveryUseCase {

    /**
     * UNKNOWN·오래된 EXECUTING 결제를 다시 승인 시도한다. 반환된 Mono 가 끝나면 이번 회차 처리가 끝난 것이다.
     */
    Mono<Void> recovery();
}
