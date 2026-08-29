package dev.deepdive.paymentsystem.payment.application.port.out;

import reactor.core.publisher.Mono;

public interface PaymentValidationPort {

    Mono<Boolean> isValid(String orderId, long amount);
}
