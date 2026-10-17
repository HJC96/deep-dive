package dev.deepdive.paymentsystem.ledger.application.exception;

/**
 * 같은 주문 라인의 장부 거래가 이미 기록돼 있다(ledger_transactions.idempotency_key UNIQUE 위반).
 * 동시에 들어온 중복 메시지 중 늦은 쪽이 받는다. 저장 트랜잭션은 롤백됐으므로 "이미 처리됨" 성공으로 다룬다.
 */
public class LedgerAlreadyRecordedException extends RuntimeException {

    public LedgerAlreadyRecordedException(String message, Throwable cause) {
        super(message, cause);
    }
}
