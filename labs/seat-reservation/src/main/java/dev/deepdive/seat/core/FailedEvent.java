package dev.deepdive.seat.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * 컨슈머가 DB 확정에 실패한 커맨드를 적재한다.
 *
 * <p>Redis 선점은 됐는데 DB 저장이 실패하면 좌석이 붕 뜬다.
 * 실패한 요청을 기록해 두면 배치가 나중에 다시 확정할 수 있다.
 */
@Entity
@Table(name = "failed_event")
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "seat_no", nullable = false)
    private String seatNo;

    protected FailedEvent() {
    }

    public FailedEvent(long userId, String seatNo) {
        this.userId = userId;
        this.seatNo = Objects.requireNonNull(seatNo, "좌석 번호는 필수입니다.");
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public String seatNo() {
        return seatNo;
    }
}
