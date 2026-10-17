package dev.deepdive.seat.store;

import dev.deepdive.seat.core.SeatReservation;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.repository.SeatReservationRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * DB에 예약을 확정한다. 좌석/사용자 단위로 멱등해 같은 좌석·사용자를 두 번 저장하지 않는다.
 *
 * <p>Redis 직접 쓰기 경로와 Kafka 컨슈머 경로가 같은 쓰기를 공유한다.
 * {@code seat.db.write-delay-millis}로 "처리량이 제한된 느린 DB"를 흉내 낼 수 있고,
 * 기본값 0이라 부하 데모 외 케이스에는 영향이 없다.
 *
 * <p>최종 방어선은 DB 유니크 제약({@code seat_no}, {@code user_id})이다. 조회로 먼저 걸러도
 * 조회-저장 사이에 다른 쓰기가 끼면 INSERT가 중복 키로 실패하는데, 그때는 다시 조회해서
 * "이미 예약됨"으로 분류한다. 같은 사용자·같은 좌석이 이미 있으면(Kafka 중복 메시지 등) 멱등하게
 * {@code ACCEPTED}를 돌려준다.
 *
 * <p>{@code @Transactional}을 일부러 걸지 않는다. 바깥 트랜잭션 안에서 중복 키 예외를 잡으면
 * 그 트랜잭션이 rollback-only로 표시되어 커밋 시점에 다시 터지기 때문에, INSERT는
 * {@code repository.save()}의 자체 트랜잭션에서 끝나게 둔다.
 */
@Component
public class SeatReservationWriter {

    private final SeatReservationRepository seatReservationRepository;
    private final long writeDelayMillis;

    public SeatReservationWriter(
            SeatReservationRepository seatReservationRepository,
            @Value("${seat.db.write-delay-millis:0}") long writeDelayMillis
    ) {
        this.seatReservationRepository = Objects.requireNonNull(seatReservationRepository, "좌석 예약 저장소는 필수입니다.");
        this.writeDelayMillis = writeDelayMillis;
    }

    /**
     * @return {@code ACCEPTED}(새로 저장했거나 같은 예약이 이미 있음),
     *         {@code SEAT_ALREADY_TAKEN}(다른 사용자가 이 좌석을 확정),
     *         {@code USER_ALREADY_HAS_SEAT}(이 사용자가 다른 좌석을 확정)
     */
    public SeatReservationStatus save(long userId, String seatNo) {
        Optional<SeatReservationStatus> existing = existingStatus(userId, seatNo);
        if (existing.isPresent()) {
            return existing.get();
        }

        simulateSlowDb();
        try {
            seatReservationRepository.save(new SeatReservation(seatNo, userId));
            return SeatReservationStatus.ACCEPTED;
        } catch (DataIntegrityViolationException e) {
            // 조회 이후 다른 쓰기가 먼저 들어와 유니크 제약에 걸린 경우 → 이미 예약됨으로 분류한다.
            // 중복 키가 아닌 무결성 위반(컬럼 길이 초과 등)이면 다시 조회해도 없으므로 그대로 던진다.
            return existingStatus(userId, seatNo).orElseThrow(() -> e);
        }
    }

    private Optional<SeatReservationStatus> existingStatus(long userId, String seatNo) {
        Optional<SeatReservation> seatOwner = seatReservationRepository.findBySeatNo(seatNo);
        if (seatOwner.isPresent()) {
            return Optional.of(seatOwner.get().userId() == userId
                    ? SeatReservationStatus.ACCEPTED
                    : SeatReservationStatus.SEAT_ALREADY_TAKEN);
        }
        if (seatReservationRepository.existsByUserId(userId)) {
            return Optional.of(SeatReservationStatus.USER_ALREADY_HAS_SEAT);
        }
        return Optional.empty();
    }

    private void simulateSlowDb() {
        if (writeDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(writeDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("느린 DB 흉내 중 인터럽트가 발생했습니다.", e);
        }
    }
}
