package dev.deepdive.seat.service;

import dev.deepdive.seat.core.SeatReservationRequestResult;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.kafka.SeatReservationCommandPublisher;
import dev.deepdive.seat.redis.SeatHoldStore;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Redis로 좌석/사용자를 선점하고, 선점에 성공한 요청만 확정 커맨드를 Kafka에 적재한 뒤 즉시 반환한다.
 *
 * <p>DB 확정은 요청 경로에서 분리되어 {@code SeatReservationConfirmConsumer}가 비동기로 처리한다.
 * 요청이 한꺼번에 몰려도 요청 경로는 Redis 선점 + Kafka publish만 하므로 DB 속도에 묶이지 않고,
 * 몰린 쓰기는 토픽이 버퍼처럼 받아 둔다.
 */
@Service
public class RedisKafkaSeatReservationService {

    private final SeatHoldStore seatHoldStore;
    private final SeatReservationCommandPublisher commandPublisher;

    public RedisKafkaSeatReservationService(
            SeatHoldStore seatHoldStore,
            SeatReservationCommandPublisher commandPublisher
    ) {
        this.seatHoldStore = Objects.requireNonNull(seatHoldStore, "좌석 선점 저장소는 필수입니다.");
        this.commandPublisher = Objects.requireNonNull(commandPublisher, "예약 커맨드 프로듀서는 필수입니다.");
    }

    public SeatReservationRequestResult reserve(long userId, String seatNo) {
        SeatReservationStatus status = seatHoldStore.hold(userId, seatNo);
        if (status != SeatReservationStatus.ACCEPTED) {
            return new SeatReservationRequestResult(userId, seatNo, status);
        }

        commandPublisher.publish(userId, seatNo);
        return new SeatReservationRequestResult(userId, seatNo, SeatReservationStatus.ACCEPTED);
    }
}
