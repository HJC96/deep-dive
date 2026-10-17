package dev.deepdive.paymentsystem.wallet.application.exception;

/**
 * 같은 주문 라인의 정산이 이미 기록돼 있다(지갑 거래 idempotency_key UNIQUE 위반).
 * 동시에 들어온 중복 메시지 중 늦은 쪽이 받는다. 정산 트랜잭션은 롤백됐으므로 "이미 처리됨" 성공으로 다룬다.
 */
public class SettlementAlreadyProcessedException extends RuntimeException {

    public SettlementAlreadyProcessedException(String message, Throwable cause) {
        super(message, cause);
    }
}
