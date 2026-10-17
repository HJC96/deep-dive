package dev.deepdive.paymentsystem.payment.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 테스트에서 토픽에 실제로 발행된 메시지를 읽어 확인한다. 매번 새 그룹으로 처음부터 읽는다.
 */
public final class KafkaTestConsumer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KafkaTestConsumer() {
    }

    public record Received(int partition, JsonNode message) {
    }

    /**
     * payload.orderId 가 주어진 집합에 드는 메시지를 모두 받거나 timeout 이 지날 때까지 읽는다.
     */
    public static Map<String, Received> consumeByOrderIds(String topic, Set<String> orderIds, Duration timeout) {
        Map<String, Received> received = new HashMap<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, PaymentIntegrationTestSupport.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (received.size() < orderIds.size() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    try {
                        JsonNode message = OBJECT_MAPPER.readTree(record.value());
                        String orderId = message.path("payload").path("orderId").asText();
                        if (orderIds.contains(orderId)) {
                            received.put(orderId, new Received(record.partition(), message));
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("메시지 파싱 실패: " + record.value(), e);
                    }
                }
            }
        }
        return received;
    }
}
