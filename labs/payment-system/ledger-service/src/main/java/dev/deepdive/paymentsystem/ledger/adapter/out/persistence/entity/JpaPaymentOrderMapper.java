package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;
import org.springframework.stereotype.Component;

@Component
public class JpaPaymentOrderMapper {

    public PaymentOrder mapToDomainEntity(JpaPaymentOrderEntity jpaPaymentOrderEntity) {
        return new PaymentOrder(
                jpaPaymentOrderEntity.id(),
                jpaPaymentOrderEntity.amount().longValue(),
                jpaPaymentOrderEntity.orderId()
        );
    }
}
