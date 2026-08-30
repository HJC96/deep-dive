package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;

public interface LedgerTransactionRepository {

    boolean isExist(PaymentEventMessage message);
}
