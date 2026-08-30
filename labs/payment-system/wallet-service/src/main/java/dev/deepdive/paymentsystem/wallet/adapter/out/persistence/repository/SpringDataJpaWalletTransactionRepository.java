package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaWalletTransactionRepository extends JpaRepository<JpaWalletTransactionEntity, Long> {

    boolean existsByOrderId(String orderId);
}
