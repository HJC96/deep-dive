package dev.deepdive.paymentsystem.payment.adapter.out.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessageType;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 외부 메시지 큐와 연동되는 테스트. 로컬 Kafka({@code localhost:9092})와 MySQL이 떠 있어야 한다.
 */
@SpringBootTest
@Tag("ExternalIntegration")
class PaymentEventMessageSenderTest {

    private static final String BROKERS = "localhost:9092";
    private static final String TOPIC = "payment";
    private static final int PARTITION_COUNT = 6;

    @Autowired
    private PaymentEventMessageSender paymentEventMessageSender;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void ensureTopic() throws InterruptedException {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BROKERS))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, PARTITION_COUNT, (short) 1))).all().get();
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("테스트 토픽 준비 실패", e);
            }
        }
    }

    @Test
    void should_send_eventMessage_to_the_partition_from_its_partitionKey() {
        Map<String, Integer> expectedPartitionByOrderId = new HashMap<>();
        for (int partitionKey = 0; partitionKey < PARTITION_COUNT; partitionKey++) {
            String orderId = UUID.randomUUID().toString();
            expectedPartitionByOrderId.put(orderId, partitionKey);

            paymentEventMessageSender.dispatch(new PaymentEventMessage(
                    PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                    Map.of("orderId", orderId),
                    Map.of("partitionKey", partitionKey)
            ));
        }

        Map<String, Integer> actualPartitionByOrderId = consumeUntil(expectedPartitionByOrderId.size(), Duration.ofSeconds(20));

        assertThat(actualPartitionByOrderId).containsAllEntriesOf(expectedPartitionByOrderId);
    }

    private Map<String, Integer> consumeUntil(int count, Duration timeout) {
        Map<String, Integer> partitionByOrderId = new HashMap<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKERS,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (partitionByOrderId.size() < count && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    try {
                        JsonNode payload = objectMapper.readTree(record.value()).path("payload");
                        partitionByOrderId.put(payload.path("orderId").asText(), record.partition());
                    } catch (Exception e) {
                        throw new IllegalStateException("메시지 파싱 실패: " + record.value(), e);
                    }
                }
            }
        }
        return partitionByOrderId;
    }
}
