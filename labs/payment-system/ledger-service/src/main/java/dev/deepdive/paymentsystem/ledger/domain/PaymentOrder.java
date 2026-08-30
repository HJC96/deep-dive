package dev.deepdive.paymentsystem.ledger.domain;

public class PaymentOrder extends Item {

    public PaymentOrder(long id, long amount, String orderId) {
        super(id, amount, orderId, ReferenceType.PAYMENT_ORDER);
    }
}
