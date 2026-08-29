package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception.PaymentValidationException;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import io.netty.handler.timeout.TimeoutException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentErrorHandler {

    private final PaymentStatusUpdatePort paymentStatusUpdatePort;

    public PaymentErrorHandler(PaymentStatusUpdatePort paymentStatusUpdatePort) {
        this.paymentStatusUpdatePort = paymentStatusUpdatePort;
    }

    public Mono<PaymentConfirmationResult> handlePaymentConfirmationError(Throwable error, PaymentConfirmCommand command) {
        PaymentStatus status;
        PaymentFailure failure;

        if (error instanceof PSPConfirmationException pspError) {
            status = pspError.paymentStatus();
            failure = new PaymentFailure(pspError.errorCode(), pspError.errorMessage());
        } else if (error instanceof PaymentValidationException) {
            status = PaymentStatus.FAILURE;
            failure = new PaymentFailure(error.getClass().getSimpleName(), messageOf(error));
        } else if (error instanceof PaymentAlreadyProcessedException alreadyProcessed) {
            return Mono.just(new PaymentConfirmationResult(
                    alreadyProcessed.status(),
                    new PaymentFailure(error.getClass().getSimpleName(), messageOf(error))
            ));
        } else if (error instanceof TimeoutException) {
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(error.getClass().getSimpleName(), messageOf(error));
        } else {
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(error.getClass().getSimpleName(), messageOf(error));
        }

        PaymentStatusUpdateCommand paymentStatusUpdateCommand = new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                status,
                null,
                failure
        );

        PaymentStatus finalStatus = status;
        PaymentFailure finalFailure = failure;
        return paymentStatusUpdatePort.updatePaymentStatus(paymentStatusUpdateCommand)
                .map(it -> new PaymentConfirmationResult(finalStatus, finalFailure));
    }

    private String messageOf(Throwable error) {
        return error.getMessage() == null ? "" : error.getMessage();
    }
}
