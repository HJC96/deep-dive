package dev.deepdive.paymentsystem.payment.test;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

public interface PaymentDatabaseHelper {

    PaymentEvent getPayments(String orderId);

    Mono<Void> clean();
}
