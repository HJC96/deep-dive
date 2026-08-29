package dev.deepdive.paymentsystem.payment.application.port.in;

import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import reactor.core.publisher.Mono;

public interface CheckoutUseCase {

    Mono<CheckoutResult> checkout(CheckoutCommand command);
}
