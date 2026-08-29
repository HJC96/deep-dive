package dev.deepdive.paymentsystem.payment.adapter.out.web.toss;

import dev.deepdive.paymentsystem.common.WebAdapter;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor.PaymentExecutor;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import reactor.core.publisher.Mono;

@WebAdapter
public class PaymentExecutorWebAdapter implements PaymentExecutorPort {

    private final PaymentExecutor paymentExecutor;

    public PaymentExecutorWebAdapter(PaymentExecutor paymentExecutor) {
        this.paymentExecutor = paymentExecutor;
    }

    @Override
    public Mono<PaymentExecutionResult> execute(PaymentConfirmCommand command) {
        return paymentExecutor.execute(command);
    }
}
