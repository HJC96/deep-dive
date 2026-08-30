package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaPaymentOrderMapper;
import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JpaPaymentOrderRepository implements PaymentOrderRepository {

    private final SpringDataJpaPaymentOrderRepository springDataJpaPaymentOrderRepository;
    private final JpaPaymentOrderMapper jpaPaymentOrderMapper;

    public JpaPaymentOrderRepository(
            SpringDataJpaPaymentOrderRepository springDataJpaPaymentOrderRepository,
            JpaPaymentOrderMapper jpaPaymentOrderMapper
    ) {
        this.springDataJpaPaymentOrderRepository = springDataJpaPaymentOrderRepository;
        this.jpaPaymentOrderMapper = jpaPaymentOrderMapper;
    }

    @Override
    public List<PaymentOrder> getPaymentOrders(String orderId) {
        return springDataJpaPaymentOrderRepository.findByOrderId(orderId).stream()
                .map(jpaPaymentOrderMapper::mapToDomainEntity)
                .toList();
    }
}
