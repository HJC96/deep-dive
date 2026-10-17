package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentOutboxRepository;
import dev.deepdive.paymentsystem.payment.application.port.out.DispatchEventMessagePort;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentEventMessagePort;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.test.KafkaTestConsumer;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 아웃박스 릴레이가 남아 있는 메시지를 실제 Kafka 로 다시 보내고, 브로커 응답 후 아웃박스를 SUCCESS 로 바꾸는지 확인한다.
 */
class PaymentEventMessageRelayServiceTest extends PaymentIntegrationTestSupport {

    @Autowired private PaymentOutboxRepository paymentOutboxRepository;
    @Autowired private LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort;
    @Autowired private DispatchEventMessagePort dispatchEventMessagePort;
    @Autowired private PaymentDatabaseHelper paymentDatabaseHelper;

    @BeforeEach
    void clean() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_dispatch_pending_outbox_messages_to_external_message_system() {
        String oldOrderId = insertOutbox();
        String freshOrderId = insertOutbox();
        // 커밋 직후 발행 중일 수 있는 메시지와 겹치지 않도록 릴레이는 1분 넘게 남은 메시지만 다룬다.
        paymentDatabaseHelper.setOutboxCreatedAtMinutesAgo(oldOrderId, 2);

        PaymentEventMessageRelayService relayService =
                new PaymentEventMessageRelayService(loadPendingPaymentEventMessagePort, dispatchEventMessagePort);

        relayService.relay().block(Duration.ofSeconds(10));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(paymentDatabaseHelper.getOutbox(oldOrderId).get("status"))
                        .isEqualTo("SUCCESS"));
        assertThat(paymentDatabaseHelper.getOutbox(freshOrderId).get("status")).isEqualTo("INIT");

        Map<String, KafkaTestConsumer.Received> received = KafkaTestConsumer.consumeByOrderIds(
                PAYMENT_TOPIC, Set.of(oldOrderId), Duration.ofSeconds(30));
        assertThat(received).containsKey(oldOrderId);
        assertThat(received.get(oldOrderId).message().path("payload").path("paymentOrders")).hasSize(2);
    }

    @Test
    void should_not_dispatch_again_once_marked_as_sent() {
        String orderId = insertOutbox();
        paymentDatabaseHelper.setOutboxCreatedAtMinutesAgo(orderId, 2);
        PaymentEventMessageRelayService relayService =
                new PaymentEventMessageRelayService(loadPendingPaymentEventMessagePort, dispatchEventMessagePort);

        relayService.relay().block(Duration.ofSeconds(10));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(paymentDatabaseHelper.getOutbox(orderId).get("status"))
                        .isEqualTo("SUCCESS"));

        assertThat(loadPendingPaymentEventMessagePort.getPendingPaymentEventMessage().collectList().block())
                .extracting(PaymentEventMessage::orderId)
                .doesNotContain(orderId);
    }

    private String insertOutbox() {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessage.paymentConfirmationSuccess(
                orderId,
                UUID.randomUUID().toString(),
                List.of(
                        new PaymentOrder(1L, 1L, 1L, 1L, orderId, 10000L, PaymentStatus.SUCCESS, false, false),
                        new PaymentOrder(2L, 1L, 2L, 2L, orderId, 20000L, PaymentStatus.SUCCESS, false, false)
                ),
                Math.abs(orderId.hashCode()) % PAYMENT_TOPIC_PARTITIONS
        );
        paymentOutboxRepository.insertOutbox(message).block();
        return orderId;
    }
}
