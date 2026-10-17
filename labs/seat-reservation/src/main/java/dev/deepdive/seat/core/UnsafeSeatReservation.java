package dev.deepdive.seat.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * 케이스 1(동시성 제어 없음) 비교군 전용 테이블.
 *
 * <p>일부러 유니크 제약을 두지 않는다. 확정 테이블({@link SeatReservation})에는 유니크 제약이 있어
 * 같은 테이블을 쓰면 DB가 이중 배정을 막아 버려, "조회-저장 경합으로 같은 좌석이 여러 명에게 배정된다"는
 * 케이스 1의 문제를 재현할 수 없다.
 */
@Entity
@Table(name = "unsafe_seat_reservation")
public class UnsafeSeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_no", nullable = false, length = 20)
    private String seatNo;

    @Column(name = "user_id", nullable = false)
    private long userId;

    protected UnsafeSeatReservation() {
    }

    public UnsafeSeatReservation(String seatNo, long userId) {
        this.seatNo = Objects.requireNonNull(seatNo, "좌석 번호는 필수입니다.");
        this.userId = userId;
    }

    public Long id() {
        return id;
    }

    public String seatNo() {
        return seatNo;
    }

    public long userId() {
        return userId;
    }
}
