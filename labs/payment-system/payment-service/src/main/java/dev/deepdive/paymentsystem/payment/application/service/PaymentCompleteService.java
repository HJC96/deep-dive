package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentCompleteUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.CompletePaymentPort;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPaymentPort;
import dev.deepdive.paymentsystem.payment.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import reactor.core.publisher.Mono;

@UseCase
public class PaymentCompleteService implements PaymentCompleteUseCase {

    private final LoadPaymentPort loadPaymentPort;
    private final CompletePaymentPort completePaymentPort;

    public PaymentCompleteService(
            LoadPaymentPort loadPaymentPort,
            CompletePaymentPort completePaymentPort
    ) {
        this.loadPaymentPort = loadPaymentPort;
        this.completePaymentPort = completePaymentPort;
    }

    @Override
    public Mono<Void> completePayment(WalletEventMessage walletEventMessage) {
        return loadPaymentPort.getPayment(walletEventMessage.orderId())
                .map(it -> {
                    it.confirmWalletUpdate();
                    return it;
                })
                .map(it -> {
                    it.completeIfDone();
                    return it;
                })
                .flatMap(completePaymentPort::complete);
    }

    @Override
    public Mono<Void> completePayment(LedgerEventMessage ledgerEventMessage) {
        return loadPaymentPort.getPayment(ledgerEventMessage.orderId())
                .map(it -> {
                    it.confirmLedgerUpdate();
                    return it;
                })
                .map(it -> {
                    it.completeIfDone();
                    return it;
                })
                .flatMap(completePaymentPort::complete);
    }
}
