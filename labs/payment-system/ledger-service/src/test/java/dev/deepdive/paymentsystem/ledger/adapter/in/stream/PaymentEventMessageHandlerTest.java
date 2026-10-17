package dev.deepdive.paymentsystem.ledger.adapter.in.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.ledger.test.LedgerIntegrationTestSupport;
import dev.deepdive.paymentsystem.ledger.test.PaymentEventMessages;
import dev.deepdive.paymentsystem.ledger.test.PaymentEventMessages.Line;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * payment 토픽 → 장부 기록 → ledger 토픽까지 실제 Kafka 로 흘려 본다. 주문 라인은 메시지에서만 온다.
 */
class PaymentEventMessageHandlerTest extends LedgerIntegrationTestSupport {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void should_record_from_the_payment_topic_and_publish_the_result_to_the_ledger_topic() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(PaymentEventMessages.paymentConfirmed(orderId, new Line(1, 1, 1500), new Line(2, 1, 2500)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger_transactions WHERE order_id = ?", Integer.class, orderId))
                        .isEqualTo(2));

        Optional<JsonNode> result = consumeFirst(LEDGER_TOPIC, orderId, Duration.ofSeconds(30));
        assertThat(result).isPresent();
        assertThat(result.get().path("type").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void should_send_a_legacy_message_without_order_lines_to_DLQ() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(new PaymentEventMessage(PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS, Map.of("orderId", orderId), Map.of()));

        assertThat(consumeFirst(DLQ_TOPIC, orderId, Duration.ofSeconds(60))).isPresent();
    }

    private void publish(PaymentEventMessage message) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ))) {
            producer.send(new ProducerRecord<>(PAYMENT_TOPIC, message.orderId(), objectMapper.writeValueAsString(message))).get();
        }
    }

    private Optional<JsonNode> consumeFirst(String topic, String orderId, Duration timeout) throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode message = objectMapper.readTree(record.value());
                    if (orderId.equals(message.path("payload").path("orderId").asText())) {
                        return Optional.of(message);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
