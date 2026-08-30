package dev.deepdive.paymentsystem.ledger.domain;

import java.util.List;

public class Ledger {

    public static List<DoubleLedgerEntry> createDoubleLedgerEntry(
            DoubleAccountsForLedger doubleAccountsForLedger,
            List<? extends Item> items
    ) {
        return items.stream()
                .map(item -> new DoubleLedgerEntry(
                        new LedgerEntry(
                                doubleAccountsForLedger.to(),
                                item.amount(),
                                LedgerEntryType.CREDIT
                        ),
                        new LedgerEntry(
                                doubleAccountsForLedger.from(),
                                item.amount(),
                                LedgerEntryType.DEBIT
                        ),
                        new LedgerTransaction(
                                item.type(),
                                item.id(),
                                item.orderId()
                        )
                ))
                .toList();
    }
}
