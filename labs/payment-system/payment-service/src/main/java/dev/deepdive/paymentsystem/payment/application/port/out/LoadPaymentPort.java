package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

public interface LoadPaymentPort {

    Mono<PaymentEvent> getPayment(String orderId);
}
