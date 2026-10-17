package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.ledger.common.IdempotencyCreator;
import dev.deepdive.paymentsystem.ledger.domain.LedgerTransaction;
import org.springframework.stereotype.Component;

@Component
public class JpaLedgerTransactionMapper {

    public JpaLedgerTransactionEntity mapToJpaEntity(LedgerTransaction ledgerTransaction) {
        return new JpaLedgerTransactionEntity(
                "LedgerService record transaction",
                ledgerTransaction.referenceId(),
                ledgerTransaction.referenceType().name(),
                ledgerTransaction.orderId(),
                IdempotencyCreator.create(idempotencySource(ledgerTransaction))
        );
    }

    /**
     * 주문 라인(orderId + 참조 종류 + 참조 id) 하나에 장부 거래는 하나다. 이 조합으로 키를 만든다.
     */
    static String idempotencySource(LedgerTransaction ledgerTransaction) {
        return String.join(":",
                ledgerTransaction.orderId(),
                ledgerTransaction.referenceType().name(),
                String.valueOf(ledgerTransaction.referenceId()));
    }
}
