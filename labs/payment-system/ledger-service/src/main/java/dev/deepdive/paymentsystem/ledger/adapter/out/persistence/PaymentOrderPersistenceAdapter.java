package dev.deepdive.paymentsystem.ledger.adapter.out.persistence;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.PaymentOrderRepository;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadPaymentOrderPort;
import dev.deepdive.paymentsystem.ledger.common.PersistenceAdapter;
import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;

import java.util.List;

@PersistenceAdapter
public class PaymentOrderPersistenceAdapter implements LoadPaymentOrderPort {

    private final PaymentOrderRepository paymentOrderRepository;

    public PaymentOrderPersistenceAdapter(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    @Override
    public List<PaymentOrder> getPaymentOrders(String orderId) {
        return paymentOrderRepository.getPaymentOrders(orderId);
    }
}
