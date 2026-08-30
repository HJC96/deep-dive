package dev.deepdive.paymentsystem.ledger.domain;

public record DoubleLedgerEntry(
        LedgerEntry credit,
        LedgerEntry debit,
        LedgerTransaction transaction
) {

    public DoubleLedgerEntry {
        if (credit.amount() != debit.amount()) {
            throw new IllegalArgumentException(
                    "a double ledger entry require that the amounts for both the credit and debit are same."
            );
        }
    }
}
