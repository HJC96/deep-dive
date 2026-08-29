package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import reactor.core.publisher.Mono;

public interface PaymentValidationRepository {

    Mono<Boolean> isValid(String orderId, long amount);
}
