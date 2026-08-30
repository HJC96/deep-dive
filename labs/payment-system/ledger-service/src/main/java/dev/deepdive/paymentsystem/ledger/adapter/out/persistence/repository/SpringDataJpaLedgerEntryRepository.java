package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaLedgerEntryRepository extends JpaRepository<JpaLedgerEntryEntity, Long> {
}
