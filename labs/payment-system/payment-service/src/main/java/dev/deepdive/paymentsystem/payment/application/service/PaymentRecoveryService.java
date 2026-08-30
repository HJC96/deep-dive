package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentRecoveryUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

@UseCase
@Profile("dev")
public class PaymentRecoveryService implements PaymentRecoveryUseCase {

    private final LoadPendingPaymentPort loadPendingPaymentPort;
    private final PaymentValidationPort paymentValidationPort;
    private final PaymentExecutorPort paymentExecutorPort;
    private final PaymentStatusUpdatePort paymentStatusUpdatePort;
    private final PaymentErrorHandler paymentErrorHandler;

    private final Scheduler scheduler = Schedulers.newSingle("recovery");

    public PaymentRecoveryService(
            LoadPendingPaymentPort loadPendingPaymentPort,
            PaymentValidationPort paymentValidationPort,
            PaymentExecutorPort paymentExecutorPort,
            PaymentStatusUpdatePort paymentStatusUpdatePort,
            PaymentErrorHandler paymentErrorHandler
    ) {
        this.loadPendingPaymentPort = loadPendingPaymentPort;
        this.paymentValidationPort = paymentValidationPort;
        this.paymentExecutorPort = paymentExecutorPort;
        this.paymentStatusUpdatePort = paymentStatusUpdatePort;
        this.paymentErrorHandler = paymentErrorHandler;
    }

    @Scheduled(fixedDelay = 180, initialDelay = 180, timeUnit = TimeUnit.SECONDS)
    @Override
    public void recovery() {
        loadPendingPaymentPort.getPendingPayments()
                .map(pendingPaymentEvent -> new PaymentConfirmCommand(
                        pendingPaymentEvent.paymentKey(),
                        pendingPaymentEvent.orderId(),
                        pendingPaymentEvent.totalAmount()
                ))
                .parallel(2)
                .runOn(Schedulers.parallel())
                .flatMap(command ->
                        paymentValidationPort.isValid(command.orderId(), command.amount()).thenReturn(command)
                                .flatMap(paymentExecutorPort::execute)
                                .flatMap(result -> paymentStatusUpdatePort.updatePaymentStatus(new PaymentStatusUpdateCommand(result)))
                                .onErrorResume(error -> paymentErrorHandler.handlePaymentConfirmationError(error, command).thenReturn(true))
                )
                .sequential()
                .subscribeOn(scheduler)
                .subscribe();
    }
}
