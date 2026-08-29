package dev.deepdive.paymentsystem.payment.domain;

public class PaymentOrder {

    private final Long sellerId;
    private final String orderId;
    private final Long productId;
    private final long amount;
    private final PaymentStatus paymentStatus;
    private boolean isLedgerUpdated = false;
    private boolean isWalletUpdated = false;

    public PaymentOrder(Long sellerId, String orderId, Long productId, long amount, PaymentStatus paymentStatus) {
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.productId = productId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
    }

    public Long sellerId() {
        return sellerId;
    }

    public String orderId() {
        return orderId;
    }

    public Long productId() {
        return productId;
    }

    public long amount() {
        return amount;
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    public boolean isLedgerUpdated() {
        return isLedgerUpdated;
    }

    public boolean isWalletUpdated() {
        return isWalletUpdated;
    }
}
