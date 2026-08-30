package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(PaymentTestConfiguration.class)
@Tag("ExternalIntegration")
class PaymentRecoveryServiceTest {

    @Autowired private LoadPendingPaymentPort loadPendingPaymentPort;
    @Autowired private PaymentValidationPort paymentValidationPort;
    @Autowired private PaymentStatusUpdatePort paymentStatusUpdatePort;
    @Autowired private CheckoutUseCase checkoutUseCase;
    @Autowired private PaymentDatabaseHelper paymentDatabaseHelper;
    @Autowired private PaymentErrorHandler paymentErrorHandler;

    @BeforeEach
    void clean() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_recovery_payments() throws InterruptedException {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent();
        PaymentExecutionResult result = createPaymentExecutionResult(command);

        PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);
        when(mockPaymentExecutorPort.execute(eq(command))).thenReturn(Mono.just(result));

        PaymentRecoveryService paymentRecoveryService = new PaymentRecoveryService(
                loadPendingPaymentPort,
                paymentValidationPort,
                mockPaymentExecutorPort,
                paymentStatusUpdatePort,
                paymentErrorHandler
        );

        paymentRecoveryService.recovery();

        Thread.sleep(10000);
    }

    @Test
    void should_fail_to_recovery_payment_when_an_unknown_exception_occurs() throws InterruptedException {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent();

        PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);
        when(mockPaymentExecutorPort.execute(eq(command))).thenThrow(new PSPConfirmationException(
                "UNKNOWN ERROR",
                "test_error_message",
                false,
                false,
                true,
                true
        ));

        PaymentRecoveryService paymentRecoveryService = new PaymentRecoveryService(
                loadPendingPaymentPort,
                paymentValidationPort,
                mockPaymentExecutorPort,
                paymentStatusUpdatePort,
                paymentErrorHandler
        );

        paymentRecoveryService.recovery();

        Thread.sleep(10000);
    }

    private PaymentConfirmCommand createUnknownStatusPaymentEvent() {
        String orderId = UUID.randomUUID().toString();
        String paymentKey = UUID.randomUUID().toString();

        CheckoutCommand checkoutCommand = new CheckoutCommand(1L, 1L, List.of(1L, 2L), orderId);
        CheckoutResult checkoutResult = checkoutUseCase.checkout(checkoutCommand).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(paymentKey, orderId, checkoutResult.amount());

        paymentStatusUpdatePort.updatePaymentStatusToExecuting(command.orderId(), command.paymentKey()).block();

        PaymentStatusUpdateCommand updateCommand = new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.UNKNOWN,
                null,
                new PaymentFailure("UNKNOWN", "UNKNOWN")
        );
        paymentStatusUpdatePort.updatePaymentStatus(updateCommand).block();

        return command;
    }

    private PaymentExecutionResult createPaymentExecutionResult(PaymentConfirmCommand command) {
        return new PaymentExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new PaymentExtraDetails(
                        PaymentType.NORMAL,
                        PaymentMethod.EASY_PAY,
                        LocalDateTime.now(),
                        "test_order_name",
                        PSPConfirmationStatus.DONE,
                        command.amount(),
                        "{}"
                ),
                null,
                true,
                false,
                false,
                false
        );
    }
}
