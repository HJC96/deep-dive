package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentCompleteUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.CompletePaymentPort;
import dev.deepdive.paymentsystem.payment.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import reactor.core.publisher.Mono;

/**
 * Wallet·Ledger 완료 이벤트를 받아 결제 완료를 집계한다.
 * 예전에는 결제를 읽어 메모리에서 플래그를 바꾼 뒤 다시 쓰는(read-modify-write) 방식이라, 두 이벤트가 동시에 오면
 * 서로의 플래그를 보지 못해 is_payment_done 이 영영 false 로 남을 수 있었다. 지금은 판단까지 DB 의 조건부 UPDATE 에 맡긴다.
 */
@UseCase
public class PaymentCompleteService implements PaymentCompleteUseCase {

    private final CompletePaymentPort completePaymentPort;

    public PaymentCompleteService(CompletePaymentPort completePaymentPort) {
        this.completePaymentPort = completePaymentPort;
    }

    @Override
    public Mono<Void> completePayment(WalletEventMessage walletEventMessage) {
        return completePaymentPort.completeWalletUpdate(walletEventMessage.orderId());
    }

    @Override
    public Mono<Void> completePayment(LedgerEventMessage ledgerEventMessage) {
        return completePaymentPort.completeLedgerUpdate(ledgerEventMessage.orderId());
    }
}
