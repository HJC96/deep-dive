package dev.deepdive.seat.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Kafka 브로커가 있는 컨텍스트에서만 로드한다.
// NewTopic 빈은 시작 시점에 KafkaAdmin이 브로커로 붙어 토픽을 만들기 때문에,
// 브로커가 없는 케이스 1·2 컨텍스트에 두면 localhost:9092로 붙으려다 실패한다.
@Configuration
@ConditionalOnProperty(name = "seat.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic seatReservationTopic() {
        return TopicBuilder.name(SeatReservationCommandPublisher.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
