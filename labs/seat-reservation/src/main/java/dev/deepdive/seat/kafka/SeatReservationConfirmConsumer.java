package dev.deepdive.seat.kafka;

import dev.deepdive.seat.core.FailedEvent;
import dev.deepdive.seat.core.SeatReservationCommand;
import dev.deepdive.seat.core.SeatReservationStatus;
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
    private static final int MAX_PAYLOAD_LENGTH = 1000;

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
        SeatReservationCommand command;
        try {
            command = SeatReservationCommand.parse(message);
        } catch (RuntimeException e) {
            // 형식이 깨진 메시지도 try 안에서 파싱해야 실패 이벤트로 남는다.
            // (밖에서 던지면 리스너 기본 에러 핸들러가 재시도 후 로그만 남기고 버린다)
            log.error("좌석 예약 커맨드 파싱 실패. message={}", message, e);
            failedEventRepository.save(FailedEvent.unparseable(truncate(message)));
            return;
        }

        try {
            SeatReservationStatus status = seatReservationWriter.save(command.userId(), command.seatNo());
            if (status != SeatReservationStatus.ACCEPTED) {
                // Redis 선점을 통과했는데 DB에 이미 다른 예약이 있다 = 선점 윈도우(TTL)가 비워진 뒤 들어온 요청.
                // DB 유니크 제약이 이중 배정을 막았으므로 재처리 대상이 아니다.
                log.warn("DB에 이미 다른 예약이 있어 확정하지 않음. userId={}, seatNo={}, status={}",
                        command.userId(), command.seatNo(), status);
            }
        } catch (Exception e) {
            // DB 확정 실패 → 카운트만 먹고 좌석이 붕 뜨지 않도록 실패 이벤트로 남긴다(재처리 배치는 향후 구현 — README).
            log.error("좌석 예약 확정 실패. userId={}, seatNo={}", command.userId(), command.seatNo(), e);
            failedEventRepository.save(new FailedEvent(command.userId(), command.seatNo(), truncate(message)));
        }
    }

    private static String truncate(String message) {
        String payload = String.valueOf(message);
        return payload.length() <= MAX_PAYLOAD_LENGTH ? payload : payload.substring(0, MAX_PAYLOAD_LENGTH);
    }
}
