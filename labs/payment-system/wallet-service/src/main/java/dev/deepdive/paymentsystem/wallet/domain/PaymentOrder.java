package dev.deepdive.paymentsystem.wallet.domain;

public class PaymentOrder extends Item {

    private final long id;
    private final long sellerId;

    public PaymentOrder(long id, long sellerId, long amount, String orderId) {
        super(amount, orderId, id, ReferenceType.PAYMENT_ORDER);
        this.id = id;
        this.sellerId = sellerId;
    }

    public long id() {
        return id;
    }

    public long sellerId() {
        return sellerId;
    }
}
