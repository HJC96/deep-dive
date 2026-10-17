package dev.deepdive.paymentsystem.payment.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Wallet·Ledger 처리 완료를 기록한다. 자기 플래그를 켜고, 모든 주문의 두 플래그가 켜졌으면 결제 완료(is_payment_done)까지
 * 한 트랜잭션 안에서 조건부 UPDATE 로 처리한다. 두 완료 이벤트가 동시에 와도 완료 표시가 빠지지 않는다.
 */
public interface CompletePaymentPort {

    Mono<Void> completeWalletUpdate(String orderId);

    Mono<Void> completeLedgerUpdate(String orderId);
}
