package dev.deepdive.seat.kafka;

import dev.deepdive.seat.core.FailedEvent;
import dev.deepdive.seat.core.SeatReservationCommand;
import dev.deepdive.seat.repository.FailedEventRepository;
import dev.deepdive.seat.store.SeatReservationWriter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 브로커가 있는 컨텍스트에서만 리스너를 띄운다. (게이팅 이유는 KafkaTopicConfig 참고)
@Component
@ConditionalOnProperty(name = "seat.kafka.enabled", havingValue = "true")
public class SeatReservationConfirmConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeatReservationConfirmConsumer.class);

    private final SeatReservationWriter seatReservationWriter;
    private final FailedEventRepository failedEventRepository;

    public SeatReservationConfirmConsumer(
            SeatReservationWriter seatReservationWriter,
            FailedEventRepository failedEventRepository
    ) {
        this.seatReservationWriter = Objects.requireNonNull(seatReservationWriter, "좌석 예약 기록기는 필수입니다.");
        this.failedEventRepository = Objects.requireNonNull(failedEventRepository, "실패 이벤트 저장소는 필수입니다.");
    }

    @KafkaListener(topics = SeatReservationCommandPublisher.TOPIC, groupId = "seat-reservation-confirm")
    public void consume(String message) {
        SeatReservationCommand command = SeatReservationCommand.parse(message);
        try {
            seatReservationWriter.save(command.userId(), command.seatNo());
        } catch (Exception e) {
            // DB 확정 실패 → 카운트만 먹고 좌석이 붕 뜨지 않도록 실패 이벤트로 남겨 배치가 재처리하게 한다.
            log.error("좌석 예약 확정 실패. userId={}, seatNo={}", command.userId(), command.seatNo(), e);
            failedEventRepository.save(new FailedEvent(command.userId(), command.seatNo()));
        }
    }
}
