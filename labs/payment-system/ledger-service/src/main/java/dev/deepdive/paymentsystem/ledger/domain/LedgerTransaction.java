package dev.deepdive.paymentsystem.ledger.domain;

public record LedgerTransaction(
        ReferenceType referenceType,
        long referenceId,
        String orderId
) {
}
