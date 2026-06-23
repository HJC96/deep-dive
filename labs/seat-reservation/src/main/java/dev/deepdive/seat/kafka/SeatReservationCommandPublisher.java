package dev.deepdive.seat.kafka;

import dev.deepdive.seat.core.SeatReservationCommand;
import java.util.Objects;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 예약 확정 커맨드를 Kafka 토픽으로 보내는 프로듀서.
 *
 * <p>좌석 번호를 메시지 키로 써서 같은 좌석 커맨드가 같은 파티션에서 순서대로 처리되게 한다.
 */
@Component
public class SeatReservationCommandPublisher {

    public static final String TOPIC = "seat-reservation";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public SeatReservationCommandPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate은 필수입니다.");
    }

    public void publish(long userId, String seatNo) {
        kafkaTemplate.send(TOPIC, seatNo, new SeatReservationCommand(userId, seatNo).serialize());
    }
}
