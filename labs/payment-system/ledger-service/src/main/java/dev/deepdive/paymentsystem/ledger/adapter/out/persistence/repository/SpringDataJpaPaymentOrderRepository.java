package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaPaymentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataJpaPaymentOrderRepository extends JpaRepository<JpaPaymentOrderEntity, Long> {

    List<JpaPaymentOrderEntity> findByOrderId(String orderId);
}
