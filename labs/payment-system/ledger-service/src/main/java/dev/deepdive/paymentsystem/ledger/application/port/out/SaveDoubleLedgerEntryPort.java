package dev.deepdive.paymentsystem.ledger.application.port.out;

import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;

import java.util.List;

public interface SaveDoubleLedgerEntryPort {

    void save(List<DoubleLedgerEntry> doubleLedgerEntries);
}
