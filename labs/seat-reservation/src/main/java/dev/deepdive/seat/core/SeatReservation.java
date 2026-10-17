package dev.deepdive.seat.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;

/**
 * 확정된 좌석 예약.
 *
 * <p>Redis 선점이 1차로 막지만, 선점 집합이 TTL로 비워지거나 Kafka 메시지가 중복돼도
 * 이중 배정·1인 다좌석이 생기지 않도록 DB 유니크 제약을 최종 방어선으로 둔다.
 * (제약이 없는 비교군은 {@link UnsafeSeatReservation})
 */
@Entity
@Table(
        name = "seat_reservation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_seat_reservation_seat_no", columnNames = "seat_no"),
                @UniqueConstraint(name = "uk_seat_reservation_user_id", columnNames = "user_id")
        }
)
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_no", nullable = false, length = 20)
    private String seatNo;

    @Column(name = "user_id", nullable = false)
    private long userId;

    protected SeatReservation() {
    }

    public SeatReservation(String seatNo, long userId) {
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
