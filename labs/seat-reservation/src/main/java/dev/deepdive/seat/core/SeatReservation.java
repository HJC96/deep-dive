package dev.deepdive.seat.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "seat_reservation")
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
