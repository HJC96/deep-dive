package dev.deepdive.seat.kafka;

import dev.deepdive.seat.core.SeatReservationCommand;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 예약 확정 커맨드를 Kafka 토픽으로 보내는 프로듀서.
 *
 * <p>좌석 번호를 메시지 키로 써서 같은 좌석 커맨드가 같은 파티션에서 순서대로 처리되게 한다.
 *
 * <p>{@code kafkaTemplate.send()}는 비동기라 반환 시점엔 프로듀서 버퍼에 넣은 것뿐이다.
 * 전송 결과 future를 그대로 돌려줘 호출부가 실패 시 보상(선점 해제)을 걸 수 있게 한다.
 * 요청 경로를 막지 않도록 여기서 {@code get()}으로 기다리지 않는다.
 */
@Component
public class SeatReservationCommandPublisher {

    public static final String TOPIC = "seat-reservation";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public SeatReservationCommandPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate은 필수입니다.");
    }

    public CompletableFuture<?> publish(long userId, String seatNo) {
        return kafkaTemplate.send(TOPIC, seatNo, new SeatReservationCommand(userId, seatNo).serialize());
    }
}
