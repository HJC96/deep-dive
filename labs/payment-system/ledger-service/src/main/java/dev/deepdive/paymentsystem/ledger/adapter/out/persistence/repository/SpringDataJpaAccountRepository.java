package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaAccountRepository extends JpaRepository<JpaAccountEntity, Long> {

    JpaAccountEntity findByName(String name);
}
