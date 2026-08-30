package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PendingPaymentEvent;
import reactor.core.publisher.Flux;

public interface LoadPendingPaymentPort {

    Flux<PendingPaymentEvent> getPendingPayments();
}
