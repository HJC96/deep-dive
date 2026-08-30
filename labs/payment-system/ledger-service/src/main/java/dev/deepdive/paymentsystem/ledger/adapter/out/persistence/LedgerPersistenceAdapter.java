package dev.deepdive.paymentsystem.ledger.adapter.out.persistence;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.LedgerEntryRepository;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.LedgerTransactionRepository;
import dev.deepdive.paymentsystem.ledger.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.SaveDoubleLedgerEntryPort;
import dev.deepdive.paymentsystem.ledger.common.PersistenceAdapter;
import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;

import java.util.List;

@PersistenceAdapter
public class LedgerPersistenceAdapter implements DuplicateMessageFilterPort, SaveDoubleLedgerEntryPort {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerPersistenceAdapter(
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public boolean isAlreadyProcess(PaymentEventMessage message) {
        return ledgerTransactionRepository.isExist(message);
    }

    @Override
    public void save(List<DoubleLedgerEntry> doubleLedgerEntries) {
        ledgerEntryRepository.save(doubleLedgerEntries);
    }
}
