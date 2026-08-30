package dev.deepdive.paymentsystem.ledger.application.port.out;

import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;

import java.util.List;

public interface LoadPaymentOrderPort {

    List<PaymentOrder> getPaymentOrders(String orderId);
}
