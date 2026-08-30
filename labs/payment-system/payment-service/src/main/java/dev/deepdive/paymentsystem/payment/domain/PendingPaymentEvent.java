package dev.deepdive.paymentsystem.payment.domain;

import java.util.List;

public record PendingPaymentEvent(
        Long paymentEventId,
        String paymentKey,
        String orderId,
        List<PendingPaymentOrder> pendingPaymentOrders
) {

    public long totalAmount() {
        return pendingPaymentOrders.stream().mapToLong(PendingPaymentOrder::amount).sum();
    }
}
