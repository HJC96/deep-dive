package dev.deepdive.paymentsystem.ledger.domain;

public record DoubleAccountsForLedger(
        Account to,
        Account from
) {
}
