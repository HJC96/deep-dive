package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import reactor.core.publisher.Flux;

public interface LoadPendingPaymentEventMessagePort {

    Flux<PaymentEventMessage> getPendingPaymentEventMessage();
}
