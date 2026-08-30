package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;
import org.springframework.stereotype.Component;

@Component
public class JpaPaymentOrderMapper {

    public PaymentOrder mapToDomainEntity(JpaPaymentOrderEntity jpaPaymentOrderEntity) {
        return new PaymentOrder(
                jpaPaymentOrderEntity.id(),
                jpaPaymentOrderEntity.sellerId(),
                jpaPaymentOrderEntity.amount().longValue(),
                jpaPaymentOrderEntity.orderId()
        );
    }
}
