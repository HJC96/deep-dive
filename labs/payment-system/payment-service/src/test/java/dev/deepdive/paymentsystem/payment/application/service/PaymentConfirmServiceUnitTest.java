package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentNotFoundException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB·PSP 없이 포트를 목으로 바꿔 승인 흐름의 분기만 확인한다.
 */
class PaymentConfirmServiceUnitTest {

    private static final PaymentConfirmCommand COMMAND = new PaymentConfirmCommand("pk", "order", 10000L);

    private final PaymentStatusUpdatePort statusUpdatePort = mock(PaymentStatusUpdatePort.class);
    private final PaymentValidationPort validationPort = mock(PaymentValidationPort.class);
    private final PaymentExecutorPort executorPort = mock(PaymentExecutorPort.class);
    private final PaymentErrorHandler errorHandler = new PaymentErrorHandler(statusUpdatePort);
    private final PaymentConfirmService service =
            new PaymentConfirmService(statusUpdatePort, validationPort, executorPort, errorHandler);

    @BeforeEach
    void setUp() {
        when(statusUpdatePort.updatePaymentStatus(any())).thenReturn(Mono.just(true));
        when(validationPort.isValid(anyString(), anyLong())).thenReturn(Mono.just(true));
    }

    @Test
    void nonexistent_order_is_reported_as_not_found_FAILURE_without_writing_or_calling_PSP() {
        when(statusUpdatePort.updatePaymentStatusToExecuting("order", "pk"))
                .thenReturn(Mono.error(new PaymentNotFoundException("order")));

        PaymentConfirmationResult result = service.confirm(COMMAND).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo(PaymentNotFoundException.ERROR_CODE);
        verify(executorPort, never()).execute(any());
        verify(statusUpdatePort, never()).updatePaymentStatus(any());
    }

    @Test
    void validation_not_found_is_also_reported_as_not_found() {
        when(statusUpdatePort.updatePaymentStatusToExecuting("order", "pk")).thenReturn(Mono.just(true));
        when(validationPort.isValid("order", 10000L)).thenReturn(Mono.error(new PaymentNotFoundException("order")));

        PaymentConfirmationResult result = service.confirm(COMMAND).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo(PaymentNotFoundException.ERROR_CODE);
        verify(statusUpdatePort, never()).updatePaymentStatus(any());
    }

    @Test
    void confirm_while_another_request_is_EXECUTING_returns_EXECUTING_without_calling_PSP() {
        when(statusUpdatePort.updatePaymentStatusToExecuting("order", "pk"))
                .thenReturn(Mono.error(new PaymentAlreadyProcessedException("처리 중", PaymentStatus.EXECUTING)));

        PaymentConfirmationResult result = service.confirm(COMMAND).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(result.message()).isEqualTo("결제가 이미 처리 중입니다.");
        verify(executorPort, never()).execute(any());
        verify(statusUpdatePort, never()).updatePaymentStatus(any());
    }

    @Test
    void success_signal_without_details_is_recorded_as_UNKNOWN_instead_of_throwing() {
        // 예전에는 여기서 PaymentStatusUpdateCommand 생성자가 IllegalArgumentException 을 던졌다.
        when(statusUpdatePort.updatePaymentStatusToExecuting("order", "pk")).thenReturn(Mono.just(true));
        when(executorPort.execute(COMMAND)).thenReturn(Mono.error(new PSPConfirmationException(
                "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.", true, false, false, false)));

        PaymentConfirmationResult result = service.confirm(COMMAND).block();

        ArgumentCaptor<PaymentStatusUpdateCommand> captor = ArgumentCaptor.forClass(PaymentStatusUpdateCommand.class);
        verify(statusUpdatePort).updatePaymentStatus(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void final_status_already_recorded_by_someone_else_is_returned_as_is() {
        when(statusUpdatePort.updatePaymentStatusToExecuting("order", "pk")).thenReturn(Mono.just(true));
        when(executorPort.execute(COMMAND)).thenReturn(Mono.error(new RuntimeException("timeout")));
        when(statusUpdatePort.updatePaymentStatus(any()))
                .thenReturn(Mono.error(new PaymentAlreadyProcessedException("이미 성공", PaymentStatus.SUCCESS)));

        PaymentConfirmationResult result = service.confirm(COMMAND).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
