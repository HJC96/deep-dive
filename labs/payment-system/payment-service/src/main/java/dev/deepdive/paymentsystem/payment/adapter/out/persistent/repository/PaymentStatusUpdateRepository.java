package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import reactor.core.publisher.Mono;

public interface PaymentStatusUpdateRepository {

    Mono<Boolean> updatePaymentStatusToExecuting(String orderId, String paymentKey);

    Mono<Boolean> updatePaymentStatus(PaymentStatusUpdateCommand command);
}
