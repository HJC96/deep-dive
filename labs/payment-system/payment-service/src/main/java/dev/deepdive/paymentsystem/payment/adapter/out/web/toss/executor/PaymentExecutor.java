package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import reactor.core.publisher.Mono;

public interface PaymentExecutor {

    Mono<PaymentExecutionResult> execute(PaymentConfirmCommand command);
}
