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
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessageType;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentIntegrationTestSupport;
import dev.deepdive.paymentsystem.payment.test.PaymentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCompleteServiceTest extends PaymentIntegrationTestSupport {

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
        when(mockPaymentExecutorPort.execute(any()))
                .thenAnswer(invocation -> Mono.just(PaymentTestFixtures.successResult(invocation.getArgument(0))));
    }

    @Test
    void should_update_payment_given_a_WalletEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        paymentCompleteUseCase.completePayment(walletEvent(orderId)).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isWalletUpdateDone()).isTrue();
        assertThat(paymentEvent.isLedgerUpdateDone()).isFalse();
        assertThat(paymentEvent.isPaymentDone()).isFalse();
    }

    @Test
    void should_update_payment_given_a_LedgerEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isLedgerUpdateDone()).isTrue();
        assertThat(paymentEvent.isWalletUpdateDone()).isFalse();
        assertThat(paymentEvent.isPaymentDone()).isFalse();
    }

    @Test
    void should_update_payment_given_a_LedgerEventMessage_and_WalletEventMessage() {
        String orderId = createPaymentEventWithSuccessStatus();

        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).block();
        paymentCompleteUseCase.completePayment(walletEvent(orderId)).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.isPaymentDone()).isTrue();
        assertThat(paymentEvent.isWalletUpdateDone()).isTrue();
        assertThat(paymentEvent.isLedgerUpdateDone()).isTrue();
    }

    @Test
    void should_be_idempotent_when_the_same_completion_event_is_redelivered() {
        String orderId = createPaymentEventWithSuccessStatus();

        paymentCompleteUseCase.completePayment(walletEvent(orderId)).block();
        paymentCompleteUseCase.completePayment(walletEvent(orderId)).block();
        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).block();
        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).block();

        assertThat(paymentDatabaseHelper.getPayments(orderId).isPaymentDone()).isTrue();
    }

    @Test
    void should_not_mark_completion_for_a_payment_that_is_not_SUCCESS() {
        String orderId = UUID.randomUUID().toString();
        checkoutUseCase.checkout(new CheckoutCommand(1L, 1L, List.of(1L, 2L), orderId)).block();

        paymentCompleteUseCase.completePayment(walletEvent(orderId)).block();
        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).block();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);
        assertThat(paymentEvent.isWalletUpdateDone()).isFalse();
        assertThat(paymentEvent.isPaymentDone()).isFalse();
    }

    /**
     * Wallet·Ledger 완료 이벤트가 동시에 와도 결제 완료(is_payment_done)가 빠지지 않아야 한다.
     * 예전 read-modify-write 구현에서는 두 요청이 서로의 플래그를 보지 못해 완료 표시가 누락될 수 있었다.
     */
    @RepeatedTest(3)
    void should_mark_payment_done_when_wallet_and_ledger_completions_arrive_concurrently() {
        List<String> orderIds = IntStream.range(0, 15)
                .mapToObj(i -> createPaymentEventWithSuccessStatus())
                .toList();

        Flux.fromIterable(orderIds)
                .flatMap(orderId -> Mono.when(
                        paymentCompleteUseCase.completePayment(walletEvent(orderId)).subscribeOn(Schedulers.parallel()),
                        paymentCompleteUseCase.completePayment(ledgerEvent(orderId)).subscribeOn(Schedulers.parallel())
                ))
                .then()
                .block();

        assertThat(orderIds)
                .allSatisfy(orderId -> {
                    PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);
                    assertThat(paymentEvent.isWalletUpdateDone()).isTrue();
                    assertThat(paymentEvent.isLedgerUpdateDone()).isTrue();
                    assertThat(paymentEvent.isPaymentDone()).as("orderId %s is_payment_done", orderId).isTrue();
                });
    }

    /**
     * Wallet·Ledger 가 보낸 결과 메시지를 실제 Kafka 토픽(wallet, ledger)으로 받아 결제 완료까지 가는지 확인한다.
     */
    @Test
    void should_complete_payment_from_wallet_and_ledger_topics() {
        String orderId = createPaymentEventWithSuccessStatus();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ))) {
            String body = """
                    {"type": "SUCCESS", "payload": {"orderId": "%s"}, "metadata": {}}
                    """.formatted(orderId);
            producer.send(new ProducerRecord<>("wallet", orderId, body));
            producer.send(new ProducerRecord<>("ledger", orderId, body));
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(paymentDatabaseHelper.getPayments(orderId).isPaymentDone()).isTrue());
    }

    private WalletEventMessage walletEvent(String orderId) {
        return new WalletEventMessage(WalletEventMessageType.SUCCESS, Map.of("orderId", orderId), Map.of());
    }

    private LedgerEventMessage ledgerEvent(String orderId) {
        return new LedgerEventMessage(LedgerEventMessageType.SUCCESS, Map.of("orderId", orderId), Map.of());
    }

    private String createPaymentEventWithSuccessStatus() {
        String orderId = UUID.randomUUID().toString();

        CheckoutResult checkoutResult = checkoutUseCase.checkout(
                new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId)).block();

        PaymentConfirmCommand paymentConfirmCommand = new PaymentConfirmCommand(
                UUID.randomUUID().toString(), orderId, checkoutResult.amount());

        PaymentConfirmService paymentConfirmService = new PaymentConfirmService(
                paymentStatusUpdatePort, paymentValidationPort, mockPaymentExecutorPort, paymentErrorHandler);

        paymentConfirmService.confirm(paymentConfirmCommand).block();

        return orderId;
    }
}
