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
 *
 * <p>DB 확정 결과를 그대로 반환한다. DB에 이미 다른 예약이 있으면 거절 상태를, 확정 중 예외가 나면
 * Redis 선점을 되돌린 뒤 예외를 던진다. (선점만 남고 DB엔 없는 "붕 뜬 좌석"을 만들지 않는다)
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

        SeatReservationStatus confirmed;
        try {
            confirmed = seatReservationWriter.save(userId, seatNo);
        } catch (RuntimeException e) {
            // DB 확정 실패(커넥션 타임아웃 등) → 선점을 풀지 않으면 아무도 못 쓰는 좌석이 TTL까지 남는다.
            seatHoldStore.release(userId, seatNo);
            throw e;
        }
        releaseMismatchedHold(userId, seatNo, confirmed);
        return new SeatReservationRequestResult(userId, seatNo, confirmed);
    }

    // Redis 선점은 통과했지만 DB에 이미 다른 예약이 있는 경우(선점 윈도우가 비워진 뒤 등),
    // DB가 진실이므로 이번 요청이 잘못 잡은 쪽만 되돌린다.
    private void releaseMismatchedHold(long userId, String seatNo, SeatReservationStatus confirmed) {
        switch (confirmed) {
            case ACCEPTED -> {
            }
            // 좌석은 다른 사람이 DB에 확정 → 좌석은 잡힌 게 맞으니 사용자만 풀어 다른 좌석을 시도하게 한다.
            case SEAT_ALREADY_TAKEN -> seatHoldStore.releaseUser(userId);
            // 사용자는 이미 다른 좌석을 DB에 확정 → 사용자는 그대로 두고 이 좌석만 푼다.
            case USER_ALREADY_HAS_SEAT -> seatHoldStore.releaseSeat(seatNo);
        }
    }
}
