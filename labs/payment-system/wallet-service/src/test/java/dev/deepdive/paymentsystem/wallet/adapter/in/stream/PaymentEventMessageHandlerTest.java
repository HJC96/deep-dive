package dev.deepdive.paymentsystem.wallet.adapter.in.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletRepository;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletTransactionRepository;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages;
import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages.Line;
import dev.deepdive.paymentsystem.wallet.test.WalletIntegrationTestSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * payment 토픽 → 정산 → wallet 토픽까지 실제 Kafka 로 흘려 본다. 주문 라인은 메시지에서만 온다(로컬 payment_orders 없음).
 */
class PaymentEventMessageHandlerTest extends WalletIntegrationTestSupport {

    @Autowired private SpringDataJpaWalletRepository springDataJpaWalletRepository;
    @Autowired private SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        springDataJpaWalletTransactionRepository.deleteAll();
        springDataJpaWalletRepository.deleteAll();
    }

    @Test
    void should_settle_from_the_payment_topic_and_publish_the_result_to_the_wallet_topic() throws Exception {
        JpaWalletEntity wallet = springDataJpaWalletRepository.save(new JpaWalletEntity(null, 7L, BigDecimal.ZERO, 0));
        String orderId = UUID.randomUUID().toString();

        publish(PaymentEventMessages.paymentConfirmed(orderId, new Line(71, 7, 1500), new Line(72, 7, 2500)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(springDataJpaWalletRepository.findById(wallet.id()).orElseThrow().balance().intValue())
                        .isEqualTo(4000));

        Optional<JsonNode> result = consumeFirst(WALLET_TOPIC, orderId, Duration.ofSeconds(30));
        assertThat(result).isPresent();
        assertThat(result.get().path("type").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void should_send_the_message_to_DLQ_when_the_seller_has_no_wallet() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(PaymentEventMessages.paymentConfirmed(orderId, new Line(81, 404, 1000)));

        Optional<JsonNode> dead = consumeFirst(DLQ_TOPIC, orderId, Duration.ofSeconds(60));
        assertThat(dead).isPresent();
        assertThat(springDataJpaWalletTransactionRepository.count()).isZero();
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
