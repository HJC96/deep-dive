package dev.deepdive.paymentsystem.payment.adapter.out.stream;

import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.payment.test.KafkaTestConsumer;
import dev.deepdive.paymentsystem.payment.test.PaymentIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시지가 metadata.partitionKey 로 지정한 파티션에 실제로 들어가는지 Testcontainers Kafka 로 확인한다.
 */
class PaymentEventMessageSenderTest extends PaymentIntegrationTestSupport {

    @Autowired
    private PaymentEventMessageSender paymentEventMessageSender;

    @Test
    void should_send_eventMessage_to_the_partition_from_its_partitionKey() {
        Map<String, Integer> expectedPartitionByOrderId = new HashMap<>();
        for (int partitionKey = 0; partitionKey < PAYMENT_TOPIC_PARTITIONS; partitionKey++) {
            String orderId = UUID.randomUUID().toString();
            expectedPartitionByOrderId.put(orderId, partitionKey);

            paymentEventMessageSender.dispatch(new PaymentEventMessage(
                    PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                    Map.of("orderId", orderId),
                    Map.of("partitionKey", partitionKey)
            ));
        }

        Map<String, Integer> actualPartitionByOrderId = KafkaTestConsumer.consumeByOrderIds(
                        PAYMENT_TOPIC, expectedPartitionByOrderId.keySet(), Duration.ofSeconds(30))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, it -> it.getValue().partition()));

        assertThat(actualPartitionByOrderId).containsAllEntriesOf(expectedPartitionByOrderId);
    }
}
