package dev.deepdive.paymentsystem.ledger.domain;

public class Item {

    private final long id;
    private final long amount;
    private final String orderId;
    private final ReferenceType type;

    public Item(long id, long amount, String orderId, ReferenceType type) {
        this.id = id;
        this.amount = amount;
        this.orderId = orderId;
        this.type = type;
    }

    public long id() {
        return id;
    }

    public long amount() {
        return amount;
    }

    public String orderId() {
        return orderId;
    }

    public ReferenceType type() {
        return type;
    }
}
