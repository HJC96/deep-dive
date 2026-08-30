package dev.deepdive.paymentsystem.wallet.domain;

public class Item {

    private final long amount;
    private final String orderId;
    private final long referenceId;
    private final ReferenceType referenceType;

    public Item(long amount, String orderId, long referenceId, ReferenceType referenceType) {
        this.amount = amount;
        this.orderId = orderId;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
    }

    public long amount() {
        return amount;
    }

    public String orderId() {
        return orderId;
    }

    public long referenceId() {
        return referenceId;
    }

    public ReferenceType referenceType() {
        return referenceType;
    }
}
