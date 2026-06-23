package dev.deepdive.seat.service;

import dev.deepdive.seat.core.SeatReservationRequestResult;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.redis.SeatHoldStore;
import dev.deepdive.seat.store.SeatReservationWriter;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Redis로 좌석/사용자를 원자적으로 선점한 뒤, 선점에 성공한 요청만 DB에 예약을 확정한다.
 *
 * <p>이중 배정과 1인 1좌석을 모두 막지만, DB 확정을 요청 스레드가 직접 수행하므로
 * 요청 경로와 DB 쓰기가 결합된다. DB가 느려지면 그 지연이 그대로 요청으로 전파된다.
 */
@Service
public class RedisSeatReservationService {

    private final SeatHoldStore seatHoldStore;
    private final SeatReservationWriter seatReservationWriter;

    public RedisSeatReservationService(
            SeatHoldStore seatHoldStore,
            SeatReservationWriter seatReservationWriter
    ) {
        this.seatHoldStore = Objects.requireNonNull(seatHoldStore, "좌석 선점 저장소는 필수입니다.");
        this.seatReservationWriter = Objects.requireNonNull(seatReservationWriter, "좌석 예약 기록기는 필수입니다.");
    }

    public SeatReservationRequestResult reserve(long userId, String seatNo) {
        SeatReservationStatus status = seatHoldStore.hold(userId, seatNo);
        if (status != SeatReservationStatus.ACCEPTED) {
            return new SeatReservationRequestResult(userId, seatNo, status);
        }

        seatReservationWriter.save(userId, seatNo);
        return new SeatReservationRequestResult(userId, seatNo, SeatReservationStatus.ACCEPTED);
    }
}
