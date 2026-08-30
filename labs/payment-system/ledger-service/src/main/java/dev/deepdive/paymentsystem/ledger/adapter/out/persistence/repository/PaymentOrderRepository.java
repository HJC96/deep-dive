package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;

import java.util.List;

public interface PaymentOrderRepository {

    List<PaymentOrder> getPaymentOrders(String orderId);
}
