package dev.deepdive.seat.store;

import dev.deepdive.seat.core.SeatReservation;
import dev.deepdive.seat.repository.SeatReservationRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB에 예약을 확정한다. 좌석/사용자 단위로 멱등해 같은 좌석·사용자를 두 번 저장하지 않는다.
 *
 * <p>Redis 직접 쓰기 경로와 Kafka 컨슈머 경로가 같은 쓰기를 공유한다.
 * {@code seat.db.write-delay-millis}로 "처리량이 제한된 느린 DB"를 흉내 낼 수 있고,
 * 기본값 0이라 부하 데모 외 케이스에는 영향이 없다.
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

    @Transactional
    public boolean save(long userId, String seatNo) {
        if (seatReservationRepository.existsBySeatNo(seatNo) || seatReservationRepository.existsByUserId(userId)) {
            return false;
        }

        simulateSlowDb();
        seatReservationRepository.save(new SeatReservation(seatNo, userId));
        return true;
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
