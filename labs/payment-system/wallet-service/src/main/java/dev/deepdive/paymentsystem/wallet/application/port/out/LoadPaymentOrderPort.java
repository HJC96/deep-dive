package dev.deepdive.paymentsystem.wallet.application.port.out;

import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;

import java.util.List;

public interface LoadPaymentOrderPort {

    List<PaymentOrder> getPaymentOrders(String orderId);
}
