package dev.deepdive.transaction.tcc;

import static dev.deepdive.transaction.tcc.TccDatabases.CANCELLED;
import static dev.deepdive.transaction.tcc.TccDatabases.CONFIRMED;
import static dev.deepdive.transaction.tcc.TccDatabases.TRIED;
import static dev.deepdive.transaction.tcc.TccDatabases.execute;
import static dev.deepdive.transaction.tcc.TccDatabases.executeUpdate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 좌석 참여자. Try에서 좌석을 잡아 두고, Confirm에서 확정으로 옮기고, Cancel에서 잡아 둔 만큼 되돌린다.
 *
 * <p>2PC 참여자와 결정적으로 다른 점은 세 단계가 각각 자기 로컬 트랜잭션을 그 자리에서 커밋한다는 것이다.
 * prepare한 채 결정을 기다리며 락을 쥐고 있지 않으니, 코디네이터가 늦거나 죽어도 다른 요청은 막히지 않는다.
 * 대신 "이 요청이 어디까지 갔는지"를 아무도 기억해 주지 않아서, 참여자가 {@code seat_tcc_log}에 직접 적는다.
 *
 * <p>그 기록이 두 가지를 감당한다. 같은 단계가 두 번 와도 한 번만 반영하는 멱등성과, Try보다 Cancel이 먼저
 * 도착했을 때 뒤늦은 Try를 거부하는 빈 취소다.
 */
final class SeatParticipant {

    private SeatParticipant() {
    }

    /**
     * 좌석을 잡아 둔다. 잡았으면 true, 남은 좌석이 모자라거나 이미 취소된 요청이면 false.
     *
     * <p>{@code reserved_count + held_count}가 정원을 넘지 않는지 UPDATE의 WHERE에서 함께 본다. 확정된
     * 좌석뿐 아니라 남이 잡아 둔 좌석도 빼고 세야 한다. 잡아 둔 좌석은 이미 커밋되어 남에게도 보이기 때문에,
     * 락 없이도 두 요청이 같은 좌석을 나눠 갖지 않는다.
     */
    static boolean tryHold(long requestId, long workshopId, int seatCount) throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            seat.setAutoCommit(false);
            try {
                Log log = lock(seat, requestId);
                if (log != null) {
                    // 이미 지나간 요청이다. 자원은 건드리지 않는다.
                    // 취소가 먼저 지나갔다면 여기서 거부해야 아무도 회수하지 않는 좌석이 남지 않는다.
                    seat.commit();
                    return !CANCELLED.equals(log.state());
                }

                int updated = executeUpdate(seat, """
                        UPDATE workshop_seat
                           SET held_count = held_count + %d
                         WHERE id = %d AND reserved_count + held_count + %d <= total_seats"""
                        .formatted(seatCount, workshopId, seatCount));
                if (updated != 1) {
                    seat.rollback();
                    return false;
                }

                execute(seat, "INSERT INTO seat_tcc_log VALUES (%d, %d, %d, '%s')"
                        .formatted(requestId, workshopId, seatCount, TRIED));
                // 잡아 둔 좌석과 그걸 적은 로그가 한 트랜잭션이라 둘 중 하나만 남는 일은 없다.
                // 커밋하는 순간 락은 모두 풀리고, 확정도 취소도 아닌 상태로 데이터가 공개된다.
                seat.commit();
                return true;
            } catch (SQLException e) {
                seat.rollback();
                throw e;
            }
        }
    }

    /**
     * 잡아 둔 좌석을 확정으로 옮긴다. TRIED가 아니면 아무것도 하지 않는다.
     *
     * <p>수량을 인자로 받지 않고 로그에 적힌 값을 읽어 쓴다. Try 때 실제로 잡아 둔 양이 얼마인지는 로그만
     * 알고 있어서, 그 값으로 되돌려야 held_count가 어긋나지 않는다.
     */
    static void confirm(long requestId) throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            seat.setAutoCommit(false);
            try {
                Log log = lock(seat, requestId);
                if (log == null || !TRIED.equals(log.state())) {
                    // 이미 확정했거나(Confirm 재시도), 취소된 요청이다. 취소된 걸 확정으로 되살리지는 않는다.
                    seat.commit();
                    return;
                }

                execute(seat, """
                        UPDATE workshop_seat
                           SET held_count = held_count - %d,
                               reserved_count = reserved_count + %d
                         WHERE id = %d"""
                        .formatted(log.seatCount(), log.seatCount(), log.workshopId()));
                execute(seat, "UPDATE seat_tcc_log SET state = '%s' WHERE request_id = %d"
                        .formatted(CONFIRMED, requestId));
                seat.commit();
            } catch (SQLException e) {
                seat.rollback();
                throw e;
            }
        }
    }

    /**
     * 잡아 둔 좌석을 되돌린다. TRIED가 아니면 자원은 건드리지 않는다.
     *
     * <p>Try 기록이 아예 없으면 빈 취소다. 그냥 넘기면 뒤늦게 도착한 Try가 좌석을 잡아 두고 아무도
     * 회수하지 않는다(매달림). 자원 대신 CANCELLED만 적어 두면 그 Try가 거부된다.
     */
    static void cancel(long requestId) throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            seat.setAutoCommit(false);
            try {
                Log log = lock(seat, requestId);
                if (log == null) {
                    // 잡아 둔 게 없으니 되돌릴 것도 없다. 워크숍과 수량은 알 수 없어 0으로 남긴다.
                    execute(seat, "INSERT INTO seat_tcc_log VALUES (%d, 0, 0, '%s')"
                            .formatted(requestId, CANCELLED));
                    seat.commit();
                    return;
                }
                if (!TRIED.equals(log.state())) {
                    // 이미 되돌렸거나(Cancel 재시도), 확정된 요청이다.
                    seat.commit();
                    return;
                }

                execute(seat, "UPDATE workshop_seat SET held_count = held_count - %d WHERE id = %d"
                        .formatted(log.seatCount(), log.workshopId()));
                execute(seat, "UPDATE seat_tcc_log SET state = '%s' WHERE request_id = %d"
                        .formatted(CANCELLED, requestId));
                seat.commit();
            } catch (SQLException e) {
                seat.rollback();
                throw e;
            }
        }
    }

    /**
     * 로그 행을 잠그고 읽는다. 기록이 없으면 null.
     *
     * <p>{@code FOR UPDATE}로 읽는 이유는 같은 요청이 동시에 두 번 들어와도 하나씩 처리되게 하기
     * 위해서다. 상태를 보고 자원을 옮기기까지가 한 트랜잭션이어야, 둘 다 TRIED를 보고 각자 좌석을
     * 되돌리는 일이 없다.
     */
    private static Log lock(Connection seat, long requestId) throws SQLException {
        String sql = "SELECT state, workshop_id, seat_count FROM seat_tcc_log WHERE request_id = %d FOR UPDATE"
                .formatted(requestId);
        try (Statement statement = seat.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return new Log(
                    resultSet.getString("state"),
                    resultSet.getLong("workshop_id"),
                    resultSet.getInt("seat_count"));
        }
    }

    private record Log(String state, long workshopId, int seatCount) {
    }
}
