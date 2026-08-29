package dev.deepdive.paymentsystem.payment.application.port.in;

import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import reactor.core.publisher.Mono;

public interface PaymentConfirmUseCase {

    Mono<PaymentConfirmationResult> confirm(PaymentConfirmCommand command);
}
