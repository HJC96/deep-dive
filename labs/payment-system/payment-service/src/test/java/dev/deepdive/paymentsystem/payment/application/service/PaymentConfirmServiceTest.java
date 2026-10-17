package dev.deepdive.paymentsystem.payment.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentNotFoundException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentValidationException;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.CheckoutResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.test.KafkaTestConsumer;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentIntegrationTestSupport;
import dev.deepdive.paymentsystem.payment.test.PaymentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmServiceTest extends PaymentIntegrationTestSupport {

    @Autowired private CheckoutUseCase checkoutUseCase;
    @Autowired private PaymentStatusUpdatePort paymentStatusUpdatePort;
    @Autowired private PaymentValidationPort paymentValidationPort;
    @Autowired private PaymentDatabaseHelper paymentDatabaseHelper;
    @Autowired private PaymentErrorHandler paymentErrorHandler;
    @Autowired private ObjectMapper objectMapper;

    private final PaymentExecutorPort mockPaymentExecutorPort = mock(PaymentExecutorPort.class);

    @BeforeEach
    void setup() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_be_marked_as_SUCCESS_if_payment_confirmation_success_in_PSP() {
        PaymentConfirmCommand command = checkout();
        PaymentExecutionResult executionResult = PaymentTestFixtures.successResult(command);
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(executionResult));

        PaymentConfirmationResult result = confirmService().confirm(command).block();
        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(command.orderId());

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentEvent.isSuccess()).isTrue();
        assertThat(paymentEvent.paymentType()).isEqualTo(executionResult.extraDetails().type());
        assertThat(paymentEvent.paymentMethod()).isEqualTo(executionResult.extraDetails().method());
        assertThat(paymentEvent.orderName()).isEqualTo(executionResult.extraDetails().orderName());
        assertThat(paymentEvent.approvedAt().truncatedTo(ChronoUnit.MINUTES))
                .isEqualTo(executionResult.extraDetails().approvedAt().truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    void should_write_order_lines_into_the_outbox_payload_on_SUCCESS() throws Exception {
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(PaymentTestFixtures.successResult(command)));

        confirmService().confirm(command).block();

        Map<String, Object> outbox = paymentDatabaseHelper.getOutbox(command.orderId());
        JsonNode payload = objectMapper.readTree((String) outbox.get("payload"));
        JsonNode metadata = objectMapper.readTree((String) outbox.get("metadata"));

        assertThat(payload.path("orderId").asText()).isEqualTo(command.orderId());
        assertThat(payload.path("paymentKey").asText()).isEqualTo(command.paymentKey());
        assertThat(payload.path("totalAmount").asLong()).isEqualTo(60000L);
        assertThat(payload.path("paymentOrders")).hasSize(3);
        long sum = 0;
        for (JsonNode line : payload.path("paymentOrders")) {
            assertThat(line.path("paymentOrderId").asLong()).isPositive();
            assertThat(line.path("sellerId").asLong()).isEqualTo(1L);
            sum += line.path("amount").asLong();
        }
        assertThat(sum).isEqualTo(60000L);
        assertThat(metadata.path("payloadVersion").asInt()).isEqualTo(2);
    }

    @Test
    void should_persist_payment_methods_with_long_names() {
        // CULTURE_GIFT_CERTIFICATE(24자)는 예전 VARCHAR(20) 컬럼에 들어가지 않았다.
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(
                PaymentTestFixtures.successResult(command, PaymentMethod.CULTURE_GIFT_CERTIFICATE)));

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).paymentMethod())
                .isEqualTo(PaymentMethod.CULTURE_GIFT_CERTIFICATE);
    }

    @Test
    void should_be_marked_as_FAILURE_if_payment_confirmation_fails_on_PSP() {
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(PaymentTestFixtures.failureResult(command)));

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).isFailure()).isTrue();
        assertThat(paymentDatabaseHelper.getOutbox(command.orderId())).isNull();
    }

    @Test
    void should_handle_PSPConfirmationException() {
        PaymentConfirmCommand command = checkout();
        PSPConfirmationException pspConfirmationException = new PSPConfirmationException(
                TossPaymentError.REJECT_ACCOUNT_PAYMENT.name(),
                TossPaymentError.REJECT_ACCOUNT_PAYMENT.description(),
                false, true, false, false);
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.error(pspConfirmationException));

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).isFailure()).isTrue();
    }

    @Test
    void should_record_failure_reason_containing_quotes_safely() {
        // Toss 메시지에는 따옴표가 들어 있다. 예전에는 이력 INSERT 를 문자열로 이어 붙여 SQL 문법 오류가 났다.
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.error(new PSPConfirmationException(
                TossPaymentError.INCORRECT_BASIC_AUTH_FORMAT.name(),
                TossPaymentError.INCORRECT_BASIC_AUTH_FORMAT.description(),
                false, true, false, false)));

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentDatabaseHelper.getHistoryReasons(command.orderId()))
                .anyMatch(reason -> reason.contains("':'"));
    }

    @Test
    void should_handle_PaymentValidationException() {
        PaymentConfirmCommand command = checkout();
        PaymentValidationPort mockPaymentValidationPort = mock(PaymentValidationPort.class);
        when(mockPaymentValidationPort.isValid(command.orderId(), command.amount()))
                .thenReturn(Mono.error(new PaymentValidationException("결제 유효성 검증에서 실패하였습니다.")));

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, mockPaymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        PaymentConfirmationResult result = paymentConfirmService.confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).isFailure()).isTrue();
        verify(mockPaymentExecutorPort, never()).execute(any());
    }

    @Test
    void should_fail_validation_when_the_amount_does_not_match() {
        PaymentConfirmCommand checkedOut = checkout();
        PaymentConfirmCommand command = new PaymentConfirmCommand(
                checkedOut.paymentKey(), checkedOut.orderId(), checkedOut.amount() - 1);

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo(PaymentValidationException.class.getSimpleName());
        verify(mockPaymentExecutorPort, never()).execute(any());
    }

    @Test
    void should_return_not_found_failure_for_a_nonexistent_orderId_without_calling_PSP() {
        String orderId = UUID.randomUUID().toString();
        PaymentConfirmCommand command = new PaymentConfirmCommand(UUID.randomUUID().toString(), orderId, 10000L);

        PaymentConfirmationResult result = confirmService().confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILURE);
        assertThat(result.failure().errorCode()).isEqualTo(PaymentNotFoundException.ERROR_CODE);
        assertThat(paymentDatabaseHelper.getPayments(orderId)).isNull();
        verify(mockPaymentExecutorPort, never()).execute(any());
    }

    @Test
    void should_handle_PaymentAlreadyProcessedException() {
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(PaymentTestFixtures.successResult(command)));

        PaymentConfirmService paymentConfirmService = confirmService();
        paymentConfirmService.confirm(command).block();
        PaymentConfirmationResult result = paymentConfirmService.confirm(command).block();

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentDatabaseHelper.getPayments(command.orderId()).paymentOrders())
                .allMatch(it -> it.paymentStatus() == PaymentStatus.SUCCESS);
        verify(mockPaymentExecutorPort, times(1)).execute(command);
    }

    @Test
    void should_not_overwrite_a_SUCCESS_payment_with_a_later_final_status() {
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(PaymentTestFixtures.successResult(command)));
        confirmService().confirm(command).block();

        PaymentStatusUpdateCommand lateFailure = new PaymentStatusUpdateCommand(
                command.paymentKey(), command.orderId(), PaymentStatus.FAILURE, null,
                new PaymentFailure("LATE", "늦게 도착한 실패"));
        PaymentStatusUpdateCommand lateUnknown = new PaymentStatusUpdateCommand(
                command.paymentKey(), command.orderId(), PaymentStatus.UNKNOWN, null,
                new PaymentFailure("LATE", "늦게 도착한 타임아웃"));

        assertThatThrownBy(() -> paymentStatusUpdatePort.updatePaymentStatus(lateFailure).block())
                .isInstanceOf(PaymentAlreadyProcessedException.class);
        assertThatThrownBy(() -> paymentStatusUpdatePort.updatePaymentStatus(lateUnknown).block())
                .isInstanceOf(PaymentAlreadyProcessedException.class);

        // 에러 핸들러를 거쳐도(예: 늦게 온 타임아웃) 덮어쓰지 않고 현재 상태를 돌려준다.
        PaymentConfirmationResult handled = paymentErrorHandler
                .handlePaymentConfirmationError(new RuntimeException("timeout"), command)
                .block();
        assertThat(handled.status()).isEqualTo(PaymentStatus.SUCCESS);

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(command.orderId());
        assertThat(paymentEvent.paymentOrders()).allMatch(it -> it.paymentStatus() == PaymentStatus.SUCCESS);
        assertThat(paymentDatabaseHelper.getFailedCounts(command.orderId())).containsOnly(0);
    }

    /**
     * 같은 주문에 승인 요청이 동시에 들어와도 EXECUTING 선점은 한 요청만 성공해야 하고, PSP 는 한 번만 호출돼야 한다.
     */
    @Test
    void should_call_PSP_only_once_when_the_same_payment_is_confirmed_concurrently() {
        PaymentConfirmCommand command = checkout();
        AtomicInteger executions = new AtomicInteger();
        when(mockPaymentExecutorPort.execute(command)).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return Mono.delay(Duration.ofMillis(300)).thenReturn(PaymentTestFixtures.successResult(command));
        });

        PaymentConfirmService paymentConfirmService = confirmService();
        List<PaymentConfirmationResult> results = Flux.range(0, 8)
                .flatMap(i -> paymentConfirmService.confirm(command).subscribeOn(Schedulers.parallel()))
                .collectList()
                .block();

        assertThat(executions.get()).isEqualTo(1);
        assertThat(results).hasSize(8);
        assertThat(results).filteredOn(it -> it.status() == PaymentStatus.SUCCESS).isNotEmpty();
        // 진행 중에 들어온 요청은 EXECUTING(처리 중), 끝난 뒤 들어온 요청은 SUCCESS(이미 처리됨)로 응답한다.
        assertThat(results).allMatch(it -> it.status() == PaymentStatus.SUCCESS || it.status() == PaymentStatus.EXECUTING);
        assertThat(paymentDatabaseHelper.getHistoryReasons(command.orderId()))
                .filteredOn("PAYMENT_CONFIRMATION_START"::equals)
                .hasSize(3); // 주문 라인 3개 × 선점 1회
    }

    @Test
    void should_send_the_event_message_to_the_external_message_system_after_the_payment_confirmation_has_been_successful() {
        PaymentConfirmCommand command = checkout();
        when(mockPaymentExecutorPort.execute(command)).thenReturn(Mono.just(PaymentTestFixtures.successResult(command)));

        confirmService().confirm(command).block();

        // 커밋 후 발행되고, 브로커 응답(record metadata)을 받으면 아웃박스가 SUCCESS 로 바뀐다.
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(paymentDatabaseHelper.getOutbox(command.orderId()).get("status"))
                        .isEqualTo("SUCCESS"));

        Map<String, KafkaTestConsumer.Received> received = KafkaTestConsumer.consumeByOrderIds(
                PAYMENT_TOPIC, Set.of(command.orderId()), Duration.ofSeconds(30));
        assertThat(received).containsKey(command.orderId());
        JsonNode message = received.get(command.orderId()).message();
        assertThat(message.path("type").asText()).isEqualTo("PAYMENT_CONFIRMATION_SUCCESS");
        assertThat(message.path("payload").path("paymentOrders")).hasSize(3);
        assertThat(received.get(command.orderId()).partition())
                .isEqualTo(message.path("metadata").path("partitionKey").asInt());
    }

    private PaymentConfirmService confirmService() {
        return new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);
    }

    private PaymentConfirmCommand checkout() {
        String orderId = UUID.randomUUID().toString();
        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();
        return new PaymentConfirmCommand(UUID.randomUUID().toString(), orderId, checkoutResult.amount());
    }
}
