package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import org.springframework.stereotype.Repository;

@Repository
public class JpaLedgerTransactionRepository implements LedgerTransactionRepository {

    private final SpringDataJpaLedgerTransactionRepository springDataJpaLedgerTransactionRepository;

    public JpaLedgerTransactionRepository(
            SpringDataJpaLedgerTransactionRepository springDataJpaLedgerTransactionRepository
    ) {
        this.springDataJpaLedgerTransactionRepository = springDataJpaLedgerTransactionRepository;
    }

    @Override
    public boolean isExist(PaymentEventMessage message) {
        return springDataJpaLedgerTransactionRepository.existsByOrderId(message.orderId());
    }
}
