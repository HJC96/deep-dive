package dev.deepdive.transaction.tcc.seat;

import static dev.deepdive.transaction.tcc.TccState.CANCELLED;
import static dev.deepdive.transaction.tcc.TccState.CONFIRMED;
import static dev.deepdive.transaction.tcc.TccState.TRIED;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 참여자. Try에서 좌석을 잡아 두고, Confirm에서 확정으로 옮기고, Cancel에서 잡아 둔 만큼 되돌린다.
 *
 * <p>2PC 참여자와 결정적으로 다른 점은 세 단계가 각각 자기 로컬 트랜잭션을 그 자리에서 커밋한다는
 * 것이다. {@code @Transactional}이 붙은 메서드 하나가 곧 한 단계이고, 메서드가 끝나면 커밋된다.
 * prepare한 채 결정을 기다리며 락을 쥐고 있지 않으니 코디네이터가 늦거나 죽어도 다른 요청은 막히지 않는다.
 *
 * <p>대신 "이 요청이 어디까지 갔는지"를 아무도 기억해 주지 않아서 참여자가 {@link SeatTccLog}에 직접
 * 적는다. 그 기록이 두 가지를 감당한다. 같은 단계가 두 번 와도 한 번만 반영하는 멱등성과, Try보다
 * Cancel이 먼저 도착했을 때 뒤늦은 Try를 거부하는 빈 취소다.
 */
@Service
public class SeatParticipant {

    private final WorkshopSeatRepository seats;
    private final SeatTccLogRepository logs;

    public SeatParticipant(WorkshopSeatRepository seats, SeatTccLogRepository logs) {
        this.seats = seats;
        this.logs = logs;
    }

    /** 좌석을 잡아 둔다. 잡았으면 true, 남은 좌석이 모자라거나 이미 취소된 요청이면 false. */
    @Transactional("seatTransactionManager")
    public boolean tryHold(long requestId, long workshopId, int seatCount) {
        SeatTccLog recorded = logs.findById(requestId).orElse(null);
        if (recorded != null) {
            // 이미 지나간 요청이다. 자원은 건드리지 않는다.
            // 취소가 먼저 지나갔다면 여기서 거부해야 아무도 회수하지 않는 좌석이 남지 않는다.
            return !CANCELLED.equals(recorded.getState());
        }

        WorkshopSeat seat = seats.findById(workshopId).orElseThrow();
        if (!seat.hold(seatCount)) {
            return false;
        }
        seats.save(seat);

        // 잡아 둔 좌석과 그걸 적은 로그가 한 트랜잭션이라 둘 중 하나만 남는 일은 없다.
        // 커밋하는 순간 락은 모두 풀리고, 확정도 취소도 아닌 상태로 데이터가 공개된다.
        logs.save(new SeatTccLog(requestId, workshopId, seatCount, TRIED));
        return true;
    }

    /**
     * 잡아 둔 좌석을 확정으로 옮긴다. TRIED가 아니면 아무것도 하지 않는다.
     *
     * <p>수량을 인자로 받지 않고 로그에 적힌 값을 쓴다. Try 때 실제로 잡아 둔 양은 로그만 알고 있다.
     */
    @Transactional("seatTransactionManager")
    public void confirm(long requestId) {
        SeatTccLog log = logs.findById(requestId).orElse(null);
        if (log == null || !TRIED.equals(log.getState())) {
            // 이미 확정했거나(Confirm 재시도), 취소된 요청이다. 취소된 걸 확정으로 되살리지는 않는다.
            return;
        }

        WorkshopSeat seat = seats.findById(log.getWorkshopId()).orElseThrow();
        seat.confirmHold(log.getSeatCount());
        seats.save(seat);

        log.changeState(CONFIRMED);
        logs.save(log);
    }

    /**
     * 잡아 둔 좌석을 되돌린다. TRIED가 아니면 자원은 건드리지 않는다.
     *
     * <p>Try 기록이 아예 없으면 빈 취소다. 그냥 넘기면 뒤늦게 도착한 Try가 좌석을 잡아 두고 아무도
     * 회수하지 않는다(매달림). 자원 대신 CANCELLED만 적어 두면 그 Try가 거부된다.
     */
    @Transactional("seatTransactionManager")
    public void cancel(long requestId) {
        SeatTccLog log = logs.findById(requestId).orElse(null);
        if (log == null) {
            // 잡아 둔 게 없으니 되돌릴 것도 없다. 워크숍과 수량은 알 수 없어 0으로 남긴다.
            logs.save(new SeatTccLog(requestId, 0L, 0, CANCELLED));
            return;
        }
        if (!TRIED.equals(log.getState())) {
            // 이미 되돌렸거나(Cancel 재시도), 확정된 요청이다.
            return;
        }

        WorkshopSeat seat = seats.findById(log.getWorkshopId()).orElseThrow();
        seat.releaseHold(log.getSeatCount());
        seats.save(seat);

        log.changeState(CANCELLED);
        logs.save(log);
    }
}
