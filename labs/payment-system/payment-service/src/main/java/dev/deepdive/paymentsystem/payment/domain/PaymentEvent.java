package dev.deepdive.paymentsystem.payment.domain;

import java.util.List;

public class PaymentEvent {

    private final Long buyerId;
    private final String orderName;
    private final String orderId;
    private final List<PaymentOrder> paymentOrders;
    private boolean isPaymentDone = false;

    public PaymentEvent(Long buyerId, String orderName, String orderId, List<PaymentOrder> paymentOrders) {
        this.buyerId = buyerId;
        this.orderName = orderName;
        this.orderId = orderId;
        this.paymentOrders = paymentOrders == null ? List.of() : paymentOrders;
    }

    public long totalAmount() {
        return paymentOrders.stream().mapToLong(PaymentOrder::amount).sum();
    }

    public boolean isPaymentDone() {
        return isPaymentDone;
    }

    public Long buyerId() {
        return buyerId;
    }

    public String orderName() {
        return orderName;
    }

    public String orderId() {
        return orderId;
    }

    public List<PaymentOrder> paymentOrders() {
        return paymentOrders;
    }
}
