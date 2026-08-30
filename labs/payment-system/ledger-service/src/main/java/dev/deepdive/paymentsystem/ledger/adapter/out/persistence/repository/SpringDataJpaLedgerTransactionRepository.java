package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaLedgerTransactionRepository
        extends JpaRepository<JpaLedgerTransactionEntity, Long> {

    boolean existsByOrderId(String orderId);
}
