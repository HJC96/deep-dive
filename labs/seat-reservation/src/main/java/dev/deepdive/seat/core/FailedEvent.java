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
 *
 * <p>형식이 깨져 파싱조차 안 되는 메시지도 버리지 않도록 원문({@code payload})을 함께 남긴다.
 * 이때는 {@code userId}/{@code seatNo}를 알 수 없으므로 비워 둔다.
 */
@Entity
@Table(name = "failed_event")
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "seat_no")
    private String seatNo;

    @Column(name = "payload", nullable = false, length = 1000)
    private String payload;

    protected FailedEvent() {
    }

    public FailedEvent(long userId, String seatNo, String payload) {
        this.userId = userId;
        this.seatNo = Objects.requireNonNull(seatNo, "좌석 번호는 필수입니다.");
        this.payload = Objects.requireNonNull(payload, "원본 메시지는 필수입니다.");
    }

    private FailedEvent(String payload) {
        this.payload = Objects.requireNonNull(payload, "원본 메시지는 필수입니다.");
    }

    // 파싱에 실패한 메시지: 원문만 남긴다.
    public static FailedEvent unparseable(String payload) {
        return new FailedEvent(payload);
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public String seatNo() {
        return seatNo;
    }

    public String payload() {
        return payload;
    }
}
