package dev.deepdive.transaction.tcc;

import static dev.deepdive.transaction.tcc.TccState.CANCELLED;
import static dev.deepdive.transaction.tcc.TccState.CONFIRMED;
import static dev.deepdive.transaction.tcc.TccState.TRIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * 좌석과 지갑이 서로 다른 데이터베이스에 있을 때, 락을 쥐지 않고 둘을 맞추는 실험.
 *
 * <p>{@code distributed-transaction-2pc}에서는 참여자가 {@code XA PREPARE}로 멈춰 서서 코디네이터의
 * 결정이 올 때까지 락을 쥐고 있었다. 여기서는 각 참여자가 Try에서 자기 로컬 트랜잭션을 곧바로 커밋한다.
 * 잡아 둔 자원({@code heldCount}·{@code frozen})은 Confirm으로 확정하거나 Cancel로 되돌린다.
 *
 * <p>바꿔 말하면 락을 쥐지 않는 값으로 "아직 확정 안 된 중간 상태가 남에게 보이는 것"을 받아들인 것이다.
 * Try만 끝난 시점에 다른 요청이 그 행을 그대로 읽고 쓸 수 있다는 게 2PC와 갈리는 지점이고,
 * {@code Try와Confirm사이에도_다른요청이막히지않는다}가 그 자리를 잰다.
 *
 * <p>코디네이터 역할은 2PC 실험실과 마찬가지로 테스트 메서드의 제어 흐름이 한다.
 */
class TryConfirmCancelTest extends TccJpaTest {

    // 예약 1이 Try만 해 둔 상태에서 끼어드는 다른 요청.
    private static final long OTHER_REQUEST_ID = REQUEST_ID + 1;

    // 막힌다면 기본값 50초를 기다린다. 안 막힌다는 걸 빨리 확인하려고 줄인다.
    private static final int LOCK_WAIT_SECONDS = 3;

    @Test
    void 전원Try에성공하면_모두Confirm한다() {
        seed(100_000);

        // --- 1단계: Try ---
        // 각 참여자가 자기 자원을 잡고 그 자리에서 커밋한다. 결정을 기다리며 멈춰 있지 않는다.
        boolean seatTried = seatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT);
        boolean walletTried = walletParticipant.tryFreeze(REQUEST_ID, USER_ID, AMOUNT);

        assertThat(seatTried).isTrue();
        assertThat(walletTried).isTrue();

        // Try만 끝난 중간 상태. 잡아 두기만 했을 뿐 확정된 것은 하나도 없다.
        // 그런데도 이 값들은 이미 커밋된 값이라 다른 트랜잭션에도 그대로 보인다. TCC가 치르는 값이다.
        assertThat(heldCount()).isEqualTo(SEAT_COUNT);
        assertThat(frozen()).isEqualTo(AMOUNT);
        assertThat(reservedCount()).isZero();
        assertThat(balance()).isEqualTo(100_000 - AMOUNT);

        // --- 2단계: Confirm ---
        // 전원 성공했으니 잡아 둔 것을 확정으로 옮긴다.
        seatParticipant.confirm(REQUEST_ID);
        walletParticipant.confirm(REQUEST_ID);

        assertThat(reservedCount()).isEqualTo(SEAT_COUNT);
        assertThat(heldCount()).isZero();
        assertThat(balance()).isEqualTo(100_000 - AMOUNT);
        assertThat(frozen()).isZero();

        // 어디까지 갔는지 참여자가 스스로 적어 둔다. 같은 Confirm이 또 와도 이 기록으로 걸러 낸다.
        assertThat(seatLogState(REQUEST_ID)).isEqualTo(CONFIRMED);
        assertThat(walletLogState(REQUEST_ID)).isEqualTo(CONFIRMED);
    }

    @Test
    void 지갑이못내면_잡아둔좌석을Cancel로되돌린다() {
        seed(50_000);

        // --- 1단계: Try ---
        boolean seatTried = seatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT);
        boolean walletTried = walletParticipant.tryFreeze(REQUEST_ID, USER_ID, AMOUNT);

        assertThat(seatTried).isTrue();    // 좌석은 이미 잡혔고, 그 상태로 커밋까지 됐다
        assertThat(walletTried).isFalse(); // 잔액 50,000으로 60,000을 묶지 못한다

        // --- 2단계: Cancel ---
        // 한 명이라도 실패하면 성공한 쪽을 되돌린다. 2PC의 롤백과 달리 반대 방향 업무 작업이다.
        // 데이터베이스가 되돌려 주는 게 아니라, 잡아 둔 만큼 빼는 UPDATE를 참여자가 직접 한 번 더 한다.
        seatParticipant.cancel(REQUEST_ID);

        assertThat(reservedCount()).isZero();
        assertThat(heldCount()).isZero();
        assertThat(balance()).isEqualTo(50_000);
        assertThat(frozen()).isZero();

        // 좌석은 Try를 했으니 되돌린 기록이 남고, 지갑은 Try 자체가 실패해 남길 기록이 없다.
        assertThat(seatLogState(REQUEST_ID)).isEqualTo(CANCELLED);
        assertThat(walletLogState(REQUEST_ID)).isNull();
    }

    @Test
    void Try와Confirm사이에도_다른요청이막히지않는다() throws SQLException {
        seed(100_000);

        // 예약 1이 좌석만 잡아 두고 멈춘다. Confirm도 Cancel도 아직 오지 않았다.
        // 2PC였다면 prepare한 채로 락을 쥐고 서 있을 자리다.
        assertThat(seatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT)).isTrue();

        // 참여자와 무관한 남의 커넥션. 락이 걸리는지를 데이터베이스 수준에서 재는 게 목적이라
        // JPA를 거치지 않고 DataSource에서 직접 꺼낸다.
        try (Connection other = seatDataSource.getConnection();
             Statement statement = other.createStatement()) {
            statement.execute("SET SESSION innodb_lock_wait_timeout = " + LOCK_WAIT_SECONDS);

            // 읽기: 잡힌 상태가 그대로 보인다.
            // 2PC의 PreparedLockTest에서는 같은 자리에서 커밋 전 옛 값 0이 보였다.
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT held_count FROM workshop_seat WHERE id = " + WORKSHOP_ID)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(SEAT_COUNT);
            }

            // 쓰기: 2PC에서 Lock wait timeout이 나던 바로 그 문장이 여기서는 통과한다.
            // 예약 1의 로컬 트랜잭션은 Try에서 이미 커밋됐으니 쥐고 있는 락이 없다.
            int updated = statement.executeUpdate(
                    "UPDATE workshop_seat SET reserved_count = reserved_count + 1 WHERE id = " + WORKSHOP_ID);

            assertThat(updated).isEqualTo(1);
        }

        // 예약 2가 같은 워크숍 좌석을 새로 잡는 것도 막히지 않는다. 미결인 예약 1과 나란히 잡힌다.
        assertThat(seatParticipant.tryHold(OTHER_REQUEST_ID, WORKSHOP_ID, 1)).isTrue();
        assertThat(heldCount()).isEqualTo(SEAT_COUNT + 1);

        // 예약 1은 그동안 계속 TRIED다. 뒤늦게 Confirm이 와도 제 몫만 확정한다.
        assertThat(seatLogState(REQUEST_ID)).isEqualTo(TRIED);
        seatParticipant.confirm(REQUEST_ID);

        assertThat(heldCount()).isEqualTo(1);                  // 예약 2가 잡아 둔 1만 남는다
        assertThat(reservedCount()).isEqualTo(SEAT_COUNT + 1); // 예약 1의 2 + 다른 커넥션이 올린 1
    }
}
