package dev.deepdive.transaction.twopc;

import static dev.deepdive.transaction.twopc.TwoDatabases.AMOUNT;
import static dev.deepdive.transaction.twopc.TwoDatabases.REQUEST_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.SEAT_COUNT;
import static dev.deepdive.transaction.twopc.TwoDatabases.USER_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.WORKSHOP_ID;
import static dev.deepdive.transaction.twopc.TwoDatabases.execute;
import static dev.deepdive.transaction.twopc.TwoDatabases.executeUpdate;
import static dev.deepdive.transaction.twopc.TwoDatabases.queryLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * 좌석과 지갑이 서로 다른 데이터베이스에 있을 때 둘을 하나의 트랜잭션으로 묶는 실험.
 *
 * <p>{@code distributed-transaction-msa}에서는 좌석이 먼저 커밋되고 지갑이 실패하면 좌석만 잡힌 상태가
 * 남았다. 여기서는 두 데이터베이스가 각각 {@code XA PREPARE}까지만 가고, 코디네이터가 양쪽 투표를 본 뒤에
 * 커밋할지 되돌릴지 정한다.
 *
 * <p>코디네이터 역할은 테스트 메서드의 제어 흐름이 한다. 실행하는 SQL은 mysql 클라이언트에 그대로 붙여
 * 넣어도 돌아간다.
 */
class TwoPhaseCommitTest {

    // 글로벌 트랜잭션 하나(gtrid = reservation-1)에 참여자별 브랜치(bqual)가 붙는다.
    private static final String SEAT_XID = "'reservation-1','seat'";
    private static final String WALLET_XID = "'reservation-1','wallet'";

    @Test
    void 양쪽_참여자_투표_찬성_모두_커밋한다() throws Exception {
        TwoDatabases.seed(100_000);

        try (Connection seat = TwoDatabases.seat();
             Connection wallet = TwoDatabases.wallet()) {

            // --- 1단계: 투표 ---
            boolean seatVote = reserveSeatsAndPrepare(seat);
            boolean walletVote = debitAndPrepare(wallet);

            assertThat(seatVote).isTrue();
            assertThat(walletVote).isTrue();

            // --- 2단계: 결정 ---
            // 전원 YES. 두 데이터베이스 모두 커밋한다.
            execute(seat, "XA COMMIT " + SEAT_XID);
            execute(wallet, "XA COMMIT " + WALLET_XID);
        }

        assertThat(reservedCount()).isEqualTo(SEAT_COUNT);
        assertThat(historyCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(100_000 - AMOUNT);
    }

    @Test
    void 지갑이_못_내면_prepare한_좌석도_롤백된다() throws Exception {
        TwoDatabases.seed(50_000);

        try (Connection seat = TwoDatabases.seat();
             Connection wallet = TwoDatabases.wallet()) {

            // --- 1단계: 투표 ---
            boolean seatVote = reserveSeatsAndPrepare(seat);
            boolean walletVote = debitAndPrepare(wallet);

            assertThat(seatVote).isTrue();   // 좌석은 이미 prepare까지 갔다
            assertThat(walletVote).isFalse(); // 잔액 50,000으로 60,000을 못 낸다

            // --- 2단계: 결정 ---
            // 한 명이라도 NO면 전원 롤백. prepare까지 간 좌석도 되돌린다.
            execute(seat, "XA ROLLBACK " + SEAT_XID);
        }

        // MSA 기준선에서는 여기서 좌석 2와 이력 1건이 남았다. 2PC에서는 남지 않는다.
        assertThat(reservedCount()).isZero();
        assertThat(historyCount()).isZero();
        assertThat(balance()).isEqualTo(50_000);
    }

    @Test
    void prepare_직후_코디네이터가_죽으면_결정이_올_때까지_락이_남는다() throws Exception {
        TwoDatabases.seed(100_000);

        // 좌석이 YES를 던진 직후 코디네이터가 죽었다. 커넥션까지 끊긴다.
        try (Connection seat = TwoDatabases.seat()) {
            assertThat(reserveSeatsAndPrepare(seat)).isTrue();
        }

        // prepare한 트랜잭션은 커넥션이 끊겨도 사라지지 않는다. 커밋을 기다리며 락을 쥐고 있다.
        assertThat(TwoDatabases.preparedSeatXids()).containsExactly(SEAT_XID);

        try (Connection other = TwoDatabases.seat()) {
            // 기본값 50초를 기다리지 않으려고 줄인다.
            execute(other, "SET SESSION innodb_lock_wait_timeout = 3");

            assertThatThrownBy(() -> executeUpdate(other,
                    "UPDATE workshop_seat SET reserved_count = reserved_count + 1 WHERE id = " + WORKSHOP_ID))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Lock wait timeout");
        }

        // 결정을 아는 쪽이 나타나야 풀린다. 참여자 혼자서는 커밋인지 롤백인지 알 수 없다.
        try (Connection resolver = TwoDatabases.seat()) {
            execute(resolver, "XA COMMIT " + SEAT_XID);
        }

        assertThat(TwoDatabases.preparedSeatXids()).isEmpty();
        assertThat(reservedCount()).isEqualTo(SEAT_COUNT);
    }

    /**
     * 좌석 브랜치. 남은 좌석이 있으면 prepare까지 가고 YES를, 없으면 롤백하고 NO를 반환한다.
     *
     * <p>{@code XA END} 뒤에도 락은 풀리지 않는다. {@code XA PREPARE}가 성공했다는 건 "무슨 일이
     * 있어도 커밋할 수 있다"는 약속이고, 그 약속을 지키려면 락을 쥐고 있어야 한다.
     */
    private boolean reserveSeatsAndPrepare(Connection seat) throws SQLException {
        execute(seat, "XA START " + SEAT_XID);
        int updated = executeUpdate(seat, """
                UPDATE workshop_seat
                   SET reserved_count = reserved_count + %d
                 WHERE id = %d AND reserved_count + %d <= total_seats"""
                .formatted(SEAT_COUNT, WORKSHOP_ID, SEAT_COUNT));
        if (updated == 1) {
            execute(seat, "INSERT INTO seat_reservation_history VALUES (%d, %d, %d, %d)"
                    .formatted(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT, AMOUNT));
        }
        execute(seat, "XA END " + SEAT_XID);

        if (updated != 1) {
            execute(seat, "XA ROLLBACK " + SEAT_XID);
            return false;
        }
        execute(seat, "XA PREPARE " + SEAT_XID);
        return true;
    }

    /** 지갑 브랜치. 잔액이 충분하면 prepare까지 가고 YES를, 부족하면 롤백하고 NO를 반환한다. */
    private boolean debitAndPrepare(Connection wallet) throws SQLException {
        execute(wallet, "XA START " + WALLET_XID);
        int updated = executeUpdate(wallet, """
                UPDATE wallet
                   SET balance = balance - %d
                 WHERE user_id = %d AND balance >= %d"""
                .formatted(AMOUNT, USER_ID, AMOUNT));
        execute(wallet, "XA END " + WALLET_XID);

        if (updated != 1) {
            execute(wallet, "XA ROLLBACK " + WALLET_XID);
            return false;
        }
        execute(wallet, "XA PREPARE " + WALLET_XID);
        return true;
    }

    private long reservedCount() throws SQLException {
        try (Connection seat = TwoDatabases.seat()) {
            return queryLong(seat, "SELECT reserved_count FROM workshop_seat WHERE id = " + WORKSHOP_ID);
        }
    }

    private long historyCount() throws SQLException {
        try (Connection seat = TwoDatabases.seat()) {
            return queryLong(seat, "SELECT COUNT(*) FROM seat_reservation_history");
        }
    }

    private long balance() throws SQLException {
        try (Connection wallet = TwoDatabases.wallet()) {
            return queryLong(wallet, "SELECT balance FROM wallet WHERE user_id = " + USER_ID);
        }
    }
}
