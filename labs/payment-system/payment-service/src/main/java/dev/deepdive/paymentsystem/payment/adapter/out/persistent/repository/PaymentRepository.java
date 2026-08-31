package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentRepository {

    Mono<Void> save(PaymentEvent paymentEvent);

    Flux<PendingPaymentEvent> getPendingPayments();

    Mono<PaymentEvent> getPayment(String orderId);

    Mono<Void> complete(PaymentEvent paymentEvent);
}
