package dev.deepdive.paymentsystem.wallet.application.port.in;

import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;

public interface SettlementUseCase {

    WalletEventMessage processSettlement(PaymentEventMessage paymentEventMessage);
}
