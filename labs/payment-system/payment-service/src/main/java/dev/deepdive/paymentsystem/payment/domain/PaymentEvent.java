package dev.deepdive.paymentsystem.payment.domain;

import java.time.LocalDateTime;
import java.util.List;

public class PaymentEvent {

    private final Long id;
    private final Long buyerId;
    private final String orderName;
    private final String orderId;
    private final String paymentKey;
    private final PaymentType paymentType;
    private final PaymentMethod paymentMethod;
    private final LocalDateTime approvedAt;
    private final List<PaymentOrder> paymentOrders;
    private boolean isPaymentDone = false;

    public PaymentEvent(Long buyerId, String orderName, String orderId, List<PaymentOrder> paymentOrders) {
        this(null, buyerId, orderName, orderId, null, null, null, null, paymentOrders);
    }

    public PaymentEvent(
            Long id,
            Long buyerId,
            String orderName,
            String orderId,
            String paymentKey,
            PaymentType paymentType,
            PaymentMethod paymentMethod,
            LocalDateTime approvedAt,
            List<PaymentOrder> paymentOrders
    ) {
        this.id = id;
        this.buyerId = buyerId;
        this.orderName = orderName;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.paymentType = paymentType;
        this.paymentMethod = paymentMethod;
        this.approvedAt = approvedAt;
        this.paymentOrders = paymentOrders == null ? List.of() : paymentOrders;
    }

    public long totalAmount() {
        return paymentOrders.stream().mapToLong(PaymentOrder::amount).sum();
    }

    public boolean isPaymentDone() {
        return isPaymentDone;
    }

    public boolean isSuccess() {
        return paymentOrders.stream().allMatch(it -> it.paymentStatus() == PaymentStatus.SUCCESS);
    }

    public boolean isFailure() {
        return paymentOrders.stream().allMatch(it -> it.paymentStatus() == PaymentStatus.FAILURE);
    }

    public boolean isUnknown() {
        return paymentOrders.stream().allMatch(it -> it.paymentStatus() == PaymentStatus.UNKNOWN);
    }

    public Long id() {
        return id;
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

    public String paymentKey() {
        return paymentKey;
    }

    public PaymentType paymentType() {
        return paymentType;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime approvedAt() {
        return approvedAt;
    }

    public List<PaymentOrder> paymentOrders() {
        return paymentOrders;
    }
}
