package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;

import java.util.List;

public interface LedgerEntryRepository {

    void save(List<DoubleLedgerEntry> doubleLedgerEntries);
}
