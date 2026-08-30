package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;

import java.util.List;

public interface PaymentOrderRepository {

    List<PaymentOrder> getPaymentOrders(String orderId);
}
