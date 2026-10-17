package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentEvent;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentIntegrationTestSupport;
import dev.deepdive.paymentsystem.payment.test.PaymentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRecoveryServiceTest extends PaymentIntegrationTestSupport {

    @Autowired private LoadPendingPaymentPort loadPendingPaymentPort;
    @Autowired private PaymentValidationPort paymentValidationPort;
    @Autowired private PaymentStatusUpdatePort paymentStatusUpdatePort;
    @Autowired private CheckoutUseCase checkoutUseCase;
    @Autowired private PaymentDatabaseHelper paymentDatabaseHelper;
    @Autowired private PaymentErrorHandler paymentErrorHandler;

    private final PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);

    @BeforeEach
    void clean() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_recovery_payments() {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent(List.of(1L, 2L));
        when(mockPaymentExecutorPort.execute(eq(command))).thenReturn(Mono.just(PaymentTestFixtures.successResult(command)));

        recoveryService().recovery().block(Duration.ofSeconds(30));

        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).paymentOrders())
                .allMatch(it -> it.paymentStatus() == PaymentStatus.SUCCESS);
        assertThat(paymentDatabaseHelper.getOutbox(command.orderId())).isNotNull();
        assertThat(paymentDatabaseHelper.getHistoryReasons(command.orderId())).contains("PAYMENT_RECOVERY_START");
    }

    @Test
    void should_fail_to_recovery_payment_when_an_unknown_exception_occurs() {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent(List.of(1L, 2L));
        when(mockPaymentExecutorPort.execute(eq(command))).thenReturn(Mono.error(new PSPConfirmationException(
                "UNKNOWN ERROR", "test_error_message", false, false, true, true)));

        recoveryService().recovery().block(Duration.ofSeconds(30));

        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).paymentOrders())
                .allMatch(it -> it.paymentStatus() == PaymentStatus.UNKNOWN);
        // 최초 UNKNOWN 기록 1회 + 복구 실패 1회
        assertThat(paymentDatabaseHelper.getFailedCounts(command.orderId())).containsOnly(2);
    }

    @Test
    void should_recover_a_payment_stuck_in_EXECUTING_for_more_than_3_minutes_only() {
        PaymentConfirmCommand stale = createExecutingPaymentEvent(List.of(1L, 2L));
        PaymentConfirmCommand fresh = createExecutingPaymentEvent(List.of(1L, 2L));
        paymentDatabaseHelper.setPaymentOrderUpdatedAtMinutesAgo(stale.orderId(), 4);
        when(mockPaymentExecutorPort.execute(any()))
                .thenAnswer(invocation -> Mono.just(PaymentTestFixtures.successResult(invocation.getArgument(0))));

        recoveryService().recovery().block(Duration.ofSeconds(30));

        assertThat(paymentDatabaseHelper.getPayments(stale.orderId()).isSuccess()).isTrue();
        assertThat(paymentDatabaseHelper.getPayments(fresh.orderId()).paymentOrders())
                .allMatch(it -> it.paymentStatus() == PaymentStatus.EXECUTING);
        verify(mockPaymentExecutorPort, never()).execute(eq(fresh));
    }

    @Test
    void should_skip_a_payment_that_another_request_already_finished() {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent(List.of(1L));
        // 복구가 조회한 뒤, 선점하기 전에 사용자 재요청이 먼저 성공시킨 상황
        paymentStatusUpdatePort.updatePaymentStatusToExecuting(command.orderId(), command.paymentKey()).block();
        paymentStatusUpdatePort.updatePaymentStatus(new PaymentStatusUpdateCommand(PaymentTestFixtures.successResult(command))).block();

        Boolean claimed = paymentStatusUpdatePort.updatePaymentStatusToExecutingForRecovery(command.orderId()).block();

        assertThat(claimed).isFalse();
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).isSuccess()).isTrue();
    }

    @Test
    void should_not_retry_after_reaching_the_failure_threshold() {
        PaymentConfirmCommand command = createUnknownStatusPaymentEvent(List.of(1L));
        when(mockPaymentExecutorPort.execute(eq(command))).thenReturn(Mono.error(new PSPConfirmationException(
                "UNKNOWN ERROR", "test_error_message", false, false, true, true)));

        PaymentRecoveryService recoveryService = recoveryService();
        for (int i = 0; i < 6; i++) {
            recoveryService.recovery().block(Duration.ofSeconds(30));
        }

        // threshold 5: 최초 UNKNOWN 1회 + 복구 4회에서 멈춘다.
        verify(mockPaymentExecutorPort, times(4)).execute(eq(command));
        assertThat(paymentDatabaseHelper.getFailedCounts(command.orderId())).containsOnly(5);
    }

    /**
     * 복구 대상은 주문 행이 아니라 결제 단위로 잘라야 한다. 예전 쿼리(주문 행 LIMIT 10)는 주문 3개짜리 결제 11건 중
     * 일부 결제의 주문만 잘라 가져와 금액 검증이 어긋났다.
     */
    @Test
    void should_load_pending_payments_per_payment_event_without_splitting_orders() {
        List<PaymentConfirmCommand> commands = IntStream.range(0, 11)
                .mapToObj(i -> createUnknownStatusPaymentEvent(List.of(1L, 2L, 3L)))
                .toList();

        List<PendingPaymentEvent> pending = loadPendingPaymentPort.getPendingPayments().collectList().block();

        assertThat(pending).hasSize(10);
        assertThat(pending).allSatisfy(event -> {
            assertThat(event.pendingPaymentOrders()).hasSize(3);
            assertThat(event.totalAmount()).isEqualTo(60000L);
        });

        when(mockPaymentExecutorPort.execute(any()))
                .thenAnswer(invocation -> Mono.just(PaymentTestFixtures.successResult(invocation.getArgument(0))));
        PaymentRecoveryService recoveryService = recoveryService();
        recoveryService.recovery().block(Duration.ofSeconds(30));
        recoveryService.recovery().block(Duration.ofSeconds(30));

        assertThat(commands).allSatisfy(command ->
                assertThat(paymentDatabaseHelper.getPayments(command.orderId()).isSuccess()).isTrue());
    }

    private PaymentRecoveryService recoveryService() {
        return new PaymentRecoveryService(
                loadPendingPaymentPort,
                paymentValidationPort,
                mockPaymentExecutorPort,
                paymentStatusUpdatePort,
                paymentErrorHandler
        );
    }

    private PaymentConfirmCommand createExecutingPaymentEvent(List<Long> productIds) {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, productIds, orderId)).block();
        PaymentConfirmCommand command = new PaymentConfirmCommand(UUID.randomUUID().toString(), orderId, checkoutResult.amount());
        paymentStatusUpdatePort.updatePaymentStatusToExecuting(command.orderId(), command.paymentKey()).block();
        return command;
    }

    private PaymentConfirmCommand createUnknownStatusPaymentEvent(List<Long> productIds) {
        PaymentConfirmCommand command = createExecutingPaymentEvent(productIds);
        paymentStatusUpdatePort.updatePaymentStatus(new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                PaymentStatus.UNKNOWN,
                null,
                new PaymentFailure("UNKNOWN", "UNKNOWN")
        )).block();
        return command;
    }
}
