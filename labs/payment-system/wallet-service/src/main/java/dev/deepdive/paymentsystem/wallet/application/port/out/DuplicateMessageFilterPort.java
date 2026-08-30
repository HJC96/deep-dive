package dev.deepdive.paymentsystem.wallet.application.port.out;

import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;

public interface DuplicateMessageFilterPort {

    boolean isAlreadyProcess(PaymentEventMessage paymentEventMessage);
}
