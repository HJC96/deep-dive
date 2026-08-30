package dev.deepdive.paymentsystem.ledger.domain;

public record LedgerEntry(
        Account account,
        long amount,
        LedgerEntryType type
) {
}
