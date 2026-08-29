package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception.PaymentValidationException;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(PaymentTestConfiguration.class)
@Tag("ExternalIntegration")
class PaymentConfirmServiceTest {

    @Autowired
    private CheckoutUseCase checkoutUseCase;

    @Autowired
    private PaymentStatusUpdatePort paymentStatusUpdatePort;

    @Autowired
    private PaymentValidationPort paymentValidationPort;

    @Autowired
    private PaymentDatabaseHelper paymentDatabaseHelper;

    @Autowired
    private PaymentErrorHandler paymentErrorHandler;

    private final PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);

    @BeforeEach
    void setup() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_be_marked_as_SUCCESS_if_payment_confirmation_success_in_PSP() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentExtraDetails extraDetails = new PaymentExtraDetails(
                PaymentType.NORMAL, PaymentMethod.EASY_PAY, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, command.amount(), "{}");
        PaymentExecutionResult executionResult = new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), extraDetails, null, true, false, false, false);

        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(executionResult));

        var result = paymentConfirmService.confirm(command).block();
        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentEvent.isSuccess()).isTrue();
        assertThat(paymentEvent.paymentType()).isEqualTo(extraDetails.type());
        assertThat(paymentEvent.paymentMethod()).isEqualTo(extraDetails.method());
        assertThat(paymentEvent.orderName()).isEqualTo(extraDetails.orderName());
        assertThat(paymentEvent.approvedAt().truncatedTo(ChronoUnit.MINUTES))
                .isEqualTo(extraDetails.approvedAt().truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    void should_be_marked_as_FAILURE_if_payment_confirmation_fails_on_PSP() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentExtraDetails extraDetails = new PaymentExtraDetails(
                PaymentType.NORMAL, PaymentMethod.EASY_PAY, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, command.amount(), "{}");
        PaymentExecutionResult executionResult = new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), extraDetails,
                new PaymentFailure("ERROR", "Test Error"), false, true, false, false);

        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(executionResult));

        var result = paymentConfirmService.confirm(command).block();
        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentEvent.isFailure()).isTrue();
    }

    @Test
    void should_handle_PSPConfirmationException() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PSPConfirmationException pspConfirmationException = new PSPConfirmationException(
                TossPaymentError.REJECT_ACCOUNT_PAYMENT.name(),
                TossPaymentError.REJECT_ACCOUNT_PAYMENT.description(),
                false, true, false, false);

        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.error(pspConfirmationException));

        var result = paymentConfirmService.confirm(command).block();
        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentEvent.isFailure()).isTrue();
    }

    @Test
    void should_handle_PaymentValidationException() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentValidationPort mockPaymentValidationPort = mock(PaymentValidationPort.class);

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, mockPaymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentValidationException paymentValidationException =
                new PaymentValidationException("결제 유효성 검증에서 실패하였습니다.");

        when(mockPaymentValidationPort.isValid(orderId, command.amount()))
                .thenReturn(Mono.error(paymentValidationException));

        var result = paymentConfirmService.confirm(command).block();
        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentEvent.isFailure()).isTrue();
    }

    @Test
    void should_handle_PaymentAlreadyProcessedException() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentExtraDetails extraDetails = new PaymentExtraDetails(
                PaymentType.NORMAL, PaymentMethod.EASY_PAY, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, command.amount(), "{}");
        PaymentExecutionResult executionResult = new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), extraDetails, null, true, false, false, false);

        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(executionResult));

        paymentConfirmService.confirm(command).block();
        var result = paymentConfirmService.confirm(command).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentEvent.paymentOrders())
                .allMatch(it -> it.paymentStatus() == PaymentStatus.SUCCESS);
    }

    @Test
    @Tag("ExternalIntegration")
    void should_send_the_event_message_to_the_external_message_system_after_the_payment_confirmation_has_been_successful()
            throws InterruptedException {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentExtraDetails extraDetails = new PaymentExtraDetails(
                PaymentType.NORMAL, PaymentMethod.EASY_PAY, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, command.amount(), "{}");
        PaymentExecutionResult executionResult = new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), extraDetails, null, true, false, false, false);

        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(executionResult));

        paymentConfirmService.confirm(command).block();

        Thread.sleep(10000);
    }
}
