package dev.deepdive.transaction.tcc;

import static dev.deepdive.transaction.tcc.TccDatabases.CANCELLED;
import static dev.deepdive.transaction.tcc.TccDatabases.CONFIRMED;
import static dev.deepdive.transaction.tcc.TccDatabases.REQUEST_ID;
import static dev.deepdive.transaction.tcc.TccDatabases.SEAT_COUNT;
import static dev.deepdive.transaction.tcc.TccDatabases.TRIED;
import static dev.deepdive.transaction.tcc.TccDatabases.WORKSHOP_ID;
import static dev.deepdive.transaction.tcc.TccDatabases.queryLong;
import static dev.deepdive.transaction.tcc.TccDatabases.queryString;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 참여자가 Confirm/Cancel을 몇 번, 어떤 순서로 받아도 좌석 수가 어긋나지 않는지 확인한다.
 *
 * <p>2PC에서는 참여자가 prepare 상태로 락을 쥔 채 버텼다. 같은 요청이 두 번 들어오든 순서가 뒤바뀌든
 * 데이터베이스가 뒤에서 막아 줬다. TCC는 Try 시점에 로컬 트랜잭션을 이미 커밋하고 락을 놓는다.
 * 락이 없어진 자리는 참여자가 자기 로그를 보고 직접 메워야 한다.
 *
 * <p>그래서 Confirm이 두 번 오는 경우, Try 없이 Cancel이 먼저 오는 경우, 그 뒤에 늦은 Try가 도착하는
 * 경우가 전부 참여자 코드의 몫이 된다. 락을 쥐지 않는 대신 늘어난 이 경우의 수가 TCC가 치르는 값이다.
 */
class TccSafetyTest {

    @BeforeEach
    void seedDatabases() throws Exception {
        // 좌석 10석, reserved_count = 0, held_count = 0. 로그도 비어 있다.
        TccDatabases.seed(100_000);
    }

    @Test
    void Confirm을두번받아도_한번만적용된다() throws Exception {
        assertThat(SeatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT)).isTrue();
        assertThat(logState()).isEqualTo(TRIED);

        // Confirm 응답이 유실되면 코디네이터는 같은 Confirm을 또 보낸다. 네트워크를 건너는 이상
        // 한 번만 도착한다는 보장이 없다. 로그 state를 보지 않고 held→reserved를 그대로 다시 옮기면
        // 2석짜리 예약이 4석이 되고, 그만큼 좌석이 초과 판매된다.
        SeatParticipant.confirm(REQUEST_ID);
        SeatParticipant.confirm(REQUEST_ID);

        assertThat(reservedCount()).isEqualTo(SEAT_COUNT);
        assertThat(heldCount()).isZero();
        assertThat(logState()).isEqualTo(CONFIRMED);
    }

    @Test
    void Cancel을두번받아도_한번만적용된다() throws Exception {
        assertThat(SeatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT)).isTrue();

        // Cancel도 똑같이 재시도된다. 되돌리기는 뺄셈이라 두 번 적용되면 held_count가 -2로 내려가고,
        // 남은 좌석을 실제보다 많게 세게 된다. 재고가 조용히 늘어나는 쪽이라 더 늦게 발견된다.
        SeatParticipant.cancel(REQUEST_ID);
        SeatParticipant.cancel(REQUEST_ID);

        assertThat(heldCount()).isZero();
        assertThat(reservedCount()).isZero();
        assertThat(logState()).isEqualTo(CANCELLED);
    }

    @Test
    void Try없이Cancel이오면_빈취소로기록된다() throws Exception {
        // Try 요청이 유실되거나 타임아웃으로 늦어져도 코디네이터는 취소를 결정하고 Cancel을 보낸다.
        // 참여자에게는 본 적 없는 요청의 Cancel이다. 되돌릴 게 없다고 그냥 넘기면 안 되고,
        // "이 요청은 취소됐다"는 사실을 남겨 둬야 뒤늦은 Try를 알아볼 수 있다.
        SeatParticipant.cancel(REQUEST_ID);

        assertThat(heldCount()).isZero();
        assertThat(reservedCount()).isZero();
        assertThat(logState()).isEqualTo(CANCELLED);
    }

    @Test
    void 빈취소뒤에도착한Try는_거부된다() throws Exception {
        // Cancel이 Try를 앞질러 도착한 상황을 만든다.
        SeatParticipant.cancel(REQUEST_ID);

        // 늦은 Try가 이제야 참여자에 닿는다. 빈 취소 기록을 보지 않고 좌석을 잡아 주면,
        // 코디네이터는 이미 취소를 끝냈으니 이 요청에 Confirm도 Cancel도 다시 보내지 않는다.
        // 잡힌 좌석 2석은 아무도 손대지 않은 채 영원히 매달린다.
        assertThat(SeatParticipant.tryHold(REQUEST_ID, WORKSHOP_ID, SEAT_COUNT)).isFalse();

        assertThat(heldCount()).isZero();
        assertThat(logState()).isEqualTo(CANCELLED);
    }

    private long heldCount() throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            return queryLong(seat, "SELECT held_count FROM workshop_seat WHERE id = " + WORKSHOP_ID);
        }
    }

    private long reservedCount() throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            return queryLong(seat, "SELECT reserved_count FROM workshop_seat WHERE id = " + WORKSHOP_ID);
        }
    }

    /** 요청이 어디까지 갔는지 참여자가 적어 둔 상태. 로그가 아예 없으면 null이다. */
    private String logState() throws SQLException {
        try (Connection seat = TccDatabases.seat()) {
            return queryString(seat, "SELECT state FROM seat_tcc_log WHERE request_id = " + REQUEST_ID);
        }
    }
}
