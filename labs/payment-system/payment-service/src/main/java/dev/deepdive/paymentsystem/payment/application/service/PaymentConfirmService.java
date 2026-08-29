package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import reactor.core.publisher.Mono;

@UseCase
public class PaymentConfirmService implements PaymentConfirmUseCase {

    private final PaymentStatusUpdatePort paymentStatusUpdatePort;
    private final PaymentValidationPort paymentValidationPort;
    private final PaymentExecutorPort paymentExecutorPort;

    public PaymentConfirmService(
            PaymentStatusUpdatePort paymentStatusUpdatePort,
            PaymentValidationPort paymentValidationPort,
            PaymentExecutorPort paymentExecutorPort
    ) {
        this.paymentStatusUpdatePort = paymentStatusUpdatePort;
        this.paymentValidationPort = paymentValidationPort;
        this.paymentExecutorPort = paymentExecutorPort;
    }

    @Override
    public Mono<PaymentConfirmationResult> confirm(PaymentConfirmCommand command) {
        return paymentStatusUpdatePort.updatePaymentStatusToExecuting(command.orderId(), command.paymentKey())
                .filterWhen(it -> paymentValidationPort.isValid(command.orderId(), command.amount()))
                .flatMap(it -> paymentExecutorPort.execute(command))
                .flatMap(it -> paymentStatusUpdatePort.updatePaymentStatus(
                        new PaymentStatusUpdateCommand(
                                it.paymentKey(),
                                it.orderId(),
                                it.paymentStatus(),
                                it.extraDetails(),
                                it.failure()
                        )
                ).thenReturn(it))
                .map(it -> new PaymentConfirmationResult(it.paymentStatus(), it.failure()));
    }
}
