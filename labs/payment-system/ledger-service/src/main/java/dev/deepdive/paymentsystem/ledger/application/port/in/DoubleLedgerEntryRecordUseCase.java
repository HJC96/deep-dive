package dev.deepdive.paymentsystem.ledger.application.port.in;

import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;

public interface DoubleLedgerEntryRecordUseCase {

    LedgerEventMessage recordDoubleLedgerEntry(PaymentEventMessage message);
}
