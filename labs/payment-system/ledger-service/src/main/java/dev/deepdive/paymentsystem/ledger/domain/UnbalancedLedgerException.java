package dev.deepdive.paymentsystem.ledger.domain;

/**
 * 복식부기 불변식(차변 합 == 대변 합, 대변 합 == 결제 총액)이 깨졌다. 장부는 append-only 라 고칠 수 없으므로 저장 전에 막는다.
 */
public class UnbalancedLedgerException extends RuntimeException {

    public UnbalancedLedgerException(String message) {
        super(message);
    }
}
