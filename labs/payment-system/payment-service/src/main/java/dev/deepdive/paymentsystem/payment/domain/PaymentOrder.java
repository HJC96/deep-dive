package dev.deepdive.paymentsystem.payment.domain;

public class PaymentOrder {

    private final Long id;
    private final Long paymentEventId;
    private final Long sellerId;
    private final Long productId;
    private final String orderId;
    private final long amount;
    private final PaymentStatus paymentStatus;
    private boolean isLedgerUpdated;
    private boolean isWalletUpdated;

    public PaymentOrder(Long sellerId, String orderId, Long productId, long amount, PaymentStatus paymentStatus) {
        this(null, null, sellerId, productId, orderId, amount, paymentStatus, false, false);
    }

    public PaymentOrder(
            Long id,
            Long paymentEventId,
            Long sellerId,
            Long productId,
            String orderId,
            long amount,
            PaymentStatus paymentStatus,
            boolean isLedgerUpdated,
            boolean isWalletUpdated
    ) {
        this.id = id;
        this.paymentEventId = paymentEventId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.isLedgerUpdated = isLedgerUpdated;
        this.isWalletUpdated = isWalletUpdated;
    }

    public void confirmWalletUpdate() {
        isWalletUpdated = true;
    }

    public void confirmLedgerUpdate() {
        isLedgerUpdated = true;
    }

    public Long id() {
        return id;
    }

    public Long paymentEventId() {
        return paymentEventId;
    }

    public Long sellerId() {
        return sellerId;
    }

    public Long productId() {
        return productId;
    }

    public String orderId() {
        return orderId;
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
