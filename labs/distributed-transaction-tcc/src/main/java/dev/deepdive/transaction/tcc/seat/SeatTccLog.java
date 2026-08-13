package dev.deepdive.transaction.tcc.seat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 좌석 참여자가 요청 하나를 어디까지 처리했는지 스스로 적어 두는 로그.
 *
 * <p>2PC라면 데이터베이스가 락을 쥔 채 기억해 줬을 내용이다. TCC는 Try에서 이미 커밋하고 락을 놓으니
 * 참여자가 직접 적어야 한다. 이 기록이 Confirm·Cancel 중복 수신을 걸러 내고, Try 없이 Cancel이 먼저
 * 온 경우(빈 취소)를 남겨 뒤늦은 Try를 거부하는 근거가 된다.
 *
 * <p>수량을 함께 적어 두는 이유는 Confirm·Cancel이 인자로 수량을 받지 않기 때문이다. Try 때 실제로
 * 잡아 둔 양이 얼마인지는 이 로그만 알고 있고, 그 값으로 되돌려야 {@code heldCount}가 어긋나지 않는다.
 */
@Entity
@Table(name = "seat_tcc_log")
public class SeatTccLog {

    @Id
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "workshop_id", nullable = false)
    private long workshopId;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Column(nullable = false, length = 20)
    private String state;

    protected SeatTccLog() {
    }

    public SeatTccLog(Long requestId, long workshopId, int seatCount, String state) {
        this.requestId = requestId;
        this.workshopId = workshopId;
        this.seatCount = seatCount;
        this.state = state;
    }

    public Long getRequestId() {
        return requestId;
    }

    public long getWorkshopId() {
        return workshopId;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public String getState() {
        return state;
    }

    /** 트랜잭션 안에서 호출하면 더티 체킹으로 UPDATE된다. */
    public void changeState(String state) {
        this.state = state;
    }
}
