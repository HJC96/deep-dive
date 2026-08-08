package dev.deepdive.transaction.twopc;

import static dev.deepdive.transaction.twopc.TwoDatabases.AMOUNT;
import static dev.deepdive.transaction.twopc.TwoDatabases.OTHER_WORKSHOP_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.REQUEST_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.SEAT_COUNT;
import static dev.deepdive.transaction.twopc.TwoDatabases.WORKSHOP_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.execute;
import static dev.deepdive.transaction.twopc.TwoDatabases.executeUpdate;
import static dev.deepdive.transaction.twopc.TwoDatabases.queryLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * prepare한 트랜잭션이 쥔 락이 다른 요청을 어디까지 막는지 확인한다.
 *
 * <p>참여자는 YES를 던진 뒤 코디네이터의 결정이 올 때까지 락을 쥐고 있다. 그동안 같은 데이터를 건드리려는
 * 다른 요청이 어떻게 되는지가 2PC의 실제 비용이다. 커밋도 롤백도 아닌 상태가 길어질수록 그만큼 오래 막힌다.
 *
 * <p>막히는 것만 보면 "테이블 전체가 잠긴다"고 오해하기 쉬워서, 안 막히는 경우도 같이 확인한다.
 */
class PreparedLockTest {

    private static final String SEAT_XID = "'reservation-1','seat'";

    // 기본값 50초를 기다리지 않으려고 줄인다. 막히는 쪽 세션에만 건다.
    private static final int LOCK_WAIT_SECONDS = 3;

    @BeforeEach
    void leaveOnePreparedTransaction() throws Exception {
        TwoDatabases.seed(100_000);

        // 예약 1번이 좌석을 잡고 prepare까지 갔다. 코디네이터의 결정은 아직 오지 않았다.
        // 커넥션을 닫아도 이 트랜잭션과 락은 그대로 남는다.
        try (Connection seat = TwoDatabases.seat()) {
            execute(seat, "XA START " + SEAT_XID);
            execute(seat, "UPDATE workshop_seat SET reserved_count = reserved_count + %d WHERE id = %d"
                    .formatted(SEAT_COUNT, WORKSHOP_ID));
            execute(seat, "INSERT INTO seat_reservation_history VALUES (%d, %d, %d, %d)"
                    .formatted(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT, AMOUNT));
            execute(seat, "XA END " + SEAT_XID);
            execute(seat, "XA PREPARE " + SEAT_XID);
        }
    }

    @Test
    void 같은_좌석_행을_업데이트하면_막힌다() throws Exception {
        // 다른 사람이 같은 워크숍 좌석을 잡으려 한다.
        assertBlocked("UPDATE workshop_seat SET reserved_count = reserved_count + 1 WHERE id = " + WORKSHOP_ID);
    }

    @Test
    void 같은_요청_ID로_이력을_삽입하면_막힌다() throws Exception {
        // 같은 예약이 재시도로 다시 들어온다. request_id가 기본 키라 중복이지만,
        // 앞 트랜잭션이 커밋될지 롤백될지 모르니 중복인지도 확정할 수 없다. 그래서 기다린다.
        assertBlocked("INSERT INTO seat_reservation_history VALUES (%d, %d, %d, %d)"
                .formatted(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT, AMOUNT));
    }

    @Test
    void 다른_요청_ID로_이력을_삽입하면_막히지_않는다() throws Exception {
        // 다른 예약의 이력은 다른 키다. 새 행을 넣는 건 막히지 않는다.
        try (Connection other = TwoDatabases.seat()) {
            execute(other, "SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_SECONDS);

            int inserted = executeUpdate(other, "INSERT INTO seat_reservation_history VALUES (%d, %d, %d, %d)"
                    .formatted(REQUEST_ID + 1, OTHER_WORKSHOP_ID, 1, AMOUNT));

            assertThat(inserted).isEqualTo(1);
        }
    }

    @Test
    void 다른_좌석_행을_업데이트하면_막히지_않는다() throws Exception {
        // 락은 테이블이 아니라 행에 걸린다. 다른 워크숍은 그대로 예약된다.
        try (Connection other = TwoDatabases.seat()) {
            execute(other, "SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_SECONDS);

            int updated = executeUpdate(other,
                    "UPDATE workshop_seat SET reserved_count = reserved_count + 1 WHERE id = " + OTHER_WORKSHOP_ID);

            assertThat(updated).isEqualTo(1);
        }
    }

    @Test
    void 읽기는_막히지_않지만_아직_옛_값이_보인다() throws Exception {
        // 읽기는 스냅샷을 보므로 막히지 않는다. 대신 아직 커밋되지 않은 좌석은 보이지 않는다.
        // 남은 좌석을 세어 예약을 받는 화면이라면, 이미 잡힌 좌석을 남아 있다고 보여 주게 된다.
        try (Connection other = TwoDatabases.seat()) {
            assertThat(queryLong(other, "SELECT reserved_count FROM workshop_seat WHERE id = " + WORKSHOP_ID))
                    .isZero();
            assertThat(queryLong(other, "SELECT COUNT(*) FROM seat_reservation_history")).isZero();
        }
    }

    private void assertBlocked(String sql) throws SQLException {
        try (Connection other = TwoDatabases.seat()) {
            execute(other, "SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_SECONDS);

            assertThatThrownBy(() -> executeUpdate(other, sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Lock wait timeout");
        }
    }
}
