package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessageType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentOutboxRepository {

    Mono<PaymentEventMessage> insertOutbox(PaymentStatusUpdateCommand command);

    Mono<Boolean> markMessageAsSent(String idempotencyKey, PaymentEventMessageType type);

    Mono<Boolean> markMessageAsFailure(String idempotencyKey, PaymentEventMessageType type);

    Flux<PaymentEventMessage> getPendingPaymentOutboxes();
}
