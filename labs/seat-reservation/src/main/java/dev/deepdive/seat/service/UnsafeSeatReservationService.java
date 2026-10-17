package dev.deepdive.seat.service;

import dev.deepdive.seat.core.SeatReservationRequestResult;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.core.UnsafeSeatReservation;
import dev.deepdive.seat.repository.UnsafeSeatReservationRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시성 제어 없이 좌석 존재 여부만 확인하고 저장한다.
 *
 * <p>여러 사용자가 같은 좌석을 동시에 노리면 조회-저장 사이 경합으로 같은 좌석이 여러 명에게 배정된다.
 * 경합을 그대로 보여 주려고 유니크 제약이 없는 비교군 테이블({@code unsafe_seat_reservation})에 쓴다.
 */
@Service
public class UnsafeSeatReservationService {

    private final UnsafeSeatReservationRepository seatReservationRepository;

    public UnsafeSeatReservationService(UnsafeSeatReservationRepository seatReservationRepository) {
        this.seatReservationRepository = Objects.requireNonNull(seatReservationRepository, "좌석 예약 저장소는 필수입니다.");
    }

    @Transactional
    public SeatReservationRequestResult reserve(long userId, String seatNo) {
        if (seatReservationRepository.existsBySeatNo(seatNo)) {
            return new SeatReservationRequestResult(userId, seatNo, SeatReservationStatus.SEAT_ALREADY_TAKEN);
        }

        // 조회와 저장 사이 간격을 벌려 동시 요청이 같은 빈 좌석을 함께 통과하는 상황을 재현
        Thread.yield();
        seatReservationRepository.save(new UnsafeSeatReservation(seatNo, userId));
        return new SeatReservationRequestResult(userId, seatNo, SeatReservationStatus.ACCEPTED);
    }
}
