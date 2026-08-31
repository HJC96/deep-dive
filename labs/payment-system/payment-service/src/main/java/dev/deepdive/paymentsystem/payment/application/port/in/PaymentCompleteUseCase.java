package dev.deepdive.paymentsystem.payment.application.port.in;

import dev.deepdive.paymentsystem.payment.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import reactor.core.publisher.Mono;

public interface PaymentCompleteUseCase {

    Mono<Void> completePayment(WalletEventMessage walletEventMessage);

    Mono<Void> completePayment(LedgerEventMessage ledgerEventMessage);
}
