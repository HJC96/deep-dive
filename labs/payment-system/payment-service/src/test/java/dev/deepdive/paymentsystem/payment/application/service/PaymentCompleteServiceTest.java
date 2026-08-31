package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentCompleteUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.payment.domain.LedgerEventMessageType;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessageType;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(PaymentTestConfiguration.class)
@Tag("ExternalIntegration")
class PaymentCompleteServiceTest {

    @Autowired private PaymentDatabaseHelper paymentDatabaseHelper;
    @Autowired private CheckoutUseCase checkoutUseCase;
    @Autowired private PaymentStatusUpdatePort paymentStatusUpdatePort;
    @Autowired private PaymentValidationPort paymentValidationPort;
    @Autowired private PaymentCompleteUseCase paymentCompleteUseCase;
    @Autowired private PaymentErrorHandler paymentErrorHandler;

    private final PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);

    @BeforeEach
    void clean() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_update_payment_given_a_WalletEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        WalletEventMessage walletEventMessage = new WalletEventMessage(
                WalletEventMessageType.SUCCESS,
                Map.of("orderId", orderId),
                Map.of()
        );

        paymentCompleteUseCase.completePayment(walletEventMessage).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isWalletUpdateDone()).isTrue();
        assertThat(paymentEvent.isLedgerUpdateDone()).isFalse();
        assertThat(paymentEvent.isPaymentDone()).isFalse();
    }

    @Test
    void should_update_payment_given_a_LedgerEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        LedgerEventMessage ledgerEventMessage = new LedgerEventMessage(
                LedgerEventMessageType.SUCCESS,
                Map.of("orderId", orderId),
                Map.of()
        );

        paymentCompleteUseCase.completePayment(ledgerEventMessage).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isLedgerUpdateDone()).isTrue();
        assertThat(paymentEvent.isWalletUpdateDone()).isFalse();
        assertThat(paymentEvent.isPaymentDone()).isFalse();
    }

    @Test
    void should_update_payment_given_a_LedgerEventMessage_and_WalletEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        LedgerEventMessage ledgerEventMessage = new LedgerEventMessage(
                LedgerEventMessageType.SUCCESS, Map.of("orderId", orderId), Map.of());
        WalletEventMessage walletEventMessage = new WalletEventMessage(
                WalletEventMessageType.SUCCESS, Map.of("orderId", orderId), Map.of());

        paymentCompleteUseCase.completePayment(ledgerEventMessage).block();
        paymentCompleteUseCase.completePayment(walletEventMessage).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isPaymentDone()).isTrue();
        assertThat(paymentEvent.isWalletUpdateDone()).isTrue();
        assertThat(paymentEvent.isLedgerUpdateDone()).isTrue();
    }

    private String createPaymentEventWithSuccessStatus() {
        String orderId = UUID.randomUUID().toString();

        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand paymentConfirmCommand = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentExtraDetails extraDetails = new PaymentExtraDetails(
                PaymentType.NORMAL, PaymentMethod.EASY_PAY, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, paymentConfirmCommand.amount(), "{}");

        PaymentExecutionResult paymentExecutionResult = new PaymentExecutionResult(
                paymentConfirmCommand.paymentKey(), paymentConfirmCommand.orderId(), extraDetails,
                null, true, false, false, false);

        when(mockPaymentExecutorPort.execute(paymentConfirmCommand)).thenReturn(Mono.just(paymentExecutionResult));

        paymentConfirmService.confirm(paymentConfirmCommand).block();

        return orderId;
    }
}
