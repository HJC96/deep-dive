package dev.deepdive.paymentsystem.payment.application.port.in;

import reactor.core.publisher.Mono;

public interface PaymentEventMessageRelayUseCase {

    /**
     * 발행되지 않았거나 발행에 실패한 아웃박스 메시지를 다시 보낸다. 반환된 Mono 가 끝나면 이번 회차 디스패치가 끝난 것이다.
     */
    Mono<Void> relay();
}
