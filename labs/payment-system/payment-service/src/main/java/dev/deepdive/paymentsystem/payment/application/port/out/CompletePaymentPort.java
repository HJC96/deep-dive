package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

public interface CompletePaymentPort {

    Mono<Void> complete(PaymentEvent paymentEvent);
}
