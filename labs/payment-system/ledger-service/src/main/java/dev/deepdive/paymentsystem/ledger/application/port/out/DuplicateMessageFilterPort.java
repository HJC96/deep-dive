package dev.deepdive.paymentsystem.ledger.application.port.out;

import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;

public interface DuplicateMessageFilterPort {

    boolean isAlreadyProcess(PaymentEventMessage message);
}
