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
                IdempotencyCreator.create(ledgerTransaction)
        );
    }
}
