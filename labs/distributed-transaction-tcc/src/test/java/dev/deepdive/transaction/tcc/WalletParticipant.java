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
 * 지갑 참여자. Try에서 돈을 얼려 두고, Confirm에서 실제로 빠져나가게 하고, Cancel에서 얼린 돈을 녹인다.
 *
 * <p>{@link SeatParticipant}와 같은 규칙으로 움직인다. 세 단계가 각각 자기 로컬 트랜잭션을 곧바로
 * 커밋하고, 어디까지 갔는지는 {@code wallet_tcc_log}에 적어 둔다.
 *
 * <p>Try에서 balance를 그대로 두고 frozen만 올리면 안 된다. 커밋한 뒤로는 락이 없어서 다음 요청이 같은
 * 돈을 그대로 볼 수 있다. balance에서 먼저 빼고 frozen에 옮겨 둬야 이중 지출이 나지 않는다. 그래서
 * Confirm은 frozen만 지우면 되고, Cancel이 balance로 되돌린다.
 */
final class WalletParticipant {

    private WalletParticipant() {
    }

    /** 돈을 얼려 둔다. 얼렸으면 true, 잔액이 모자라거나 이미 취소된 요청이면 false. */
    static boolean tryFreeze(long requestId, long userId, long amount) throws SQLException {
        try (Connection wallet = TccDatabases.wallet()) {
            wallet.setAutoCommit(false);
            try {
                Log log = lock(wallet, requestId);
                if (log != null) {
                    // 이미 지나간 요청이다. 자원은 건드리지 않는다.
                    // 취소가 먼저 지나갔다면 여기서 거부해야 아무도 녹이지 않는 돈이 남지 않는다.
                    wallet.commit();
                    return !CANCELLED.equals(log.state());
                }

                int updated = executeUpdate(wallet, """
                        UPDATE wallet
                           SET balance = balance - %d,
                               frozen = frozen + %d
                         WHERE user_id = %d AND balance >= %d"""
                        .formatted(amount, amount, userId, amount));
                if (updated != 1) {
                    wallet.rollback();
                    return false;
                }

                execute(wallet, "INSERT INTO wallet_tcc_log VALUES (%d, %d, %d, '%s')"
                        .formatted(requestId, userId, amount, TRIED));
                // 얼린 돈과 그걸 적은 로그가 한 트랜잭션이라 둘 중 하나만 남는 일은 없다.
                // 커밋하는 순간 락은 모두 풀리고, 확정도 취소도 아닌 상태로 데이터가 공개된다.
                wallet.commit();
                return true;
            } catch (SQLException e) {
                wallet.rollback();
                throw e;
            }
        }
    }

    /**
     * 얼려 둔 돈을 확정으로 지운다. TRIED가 아니면 아무것도 하지 않는다.
     *
     * <p>balance는 Try에서 이미 빠졌으니 frozen만 줄이면 끝이다. 금액을 인자로 받지 않고 로그에 적힌
     * 값을 읽어 쓴다. Try 때 실제로 얼린 금액은 로그만 알고 있다.
     */
    static void confirm(long requestId) throws SQLException {
        try (Connection wallet = TccDatabases.wallet()) {
            wallet.setAutoCommit(false);
            try {
                Log log = lock(wallet, requestId);
                if (log == null || !TRIED.equals(log.state())) {
                    // 이미 확정했거나(Confirm 재시도), 취소된 요청이다. 취소된 걸 확정으로 되살리지는 않는다.
                    wallet.commit();
                    return;
                }

                execute(wallet, "UPDATE wallet SET frozen = frozen - %d WHERE user_id = %d"
                        .formatted(log.amount(), log.userId()));
                execute(wallet, "UPDATE wallet_tcc_log SET state = '%s' WHERE request_id = %d"
                        .formatted(CONFIRMED, requestId));
                wallet.commit();
            } catch (SQLException e) {
                wallet.rollback();
                throw e;
            }
        }
    }

    /**
     * 얼려 둔 돈을 잔액으로 되돌린다. TRIED가 아니면 자원은 건드리지 않는다.
     *
     * <p>Try 기록이 아예 없으면 빈 취소다. 그냥 넘기면 뒤늦게 도착한 Try가 돈을 얼려 두고 아무도
     * 녹이지 않는다(매달림). 자원 대신 CANCELLED만 적어 두면 그 Try가 거부된다.
     */
    static void cancel(long requestId) throws SQLException {
        try (Connection wallet = TccDatabases.wallet()) {
            wallet.setAutoCommit(false);
            try {
                Log log = lock(wallet, requestId);
                if (log == null) {
                    // 얼린 게 없으니 되돌릴 것도 없다. 사용자와 금액은 알 수 없어 0으로 남긴다.
                    execute(wallet, "INSERT INTO wallet_tcc_log VALUES (%d, 0, 0, '%s')"
                            .formatted(requestId, CANCELLED));
                    wallet.commit();
                    return;
                }
                if (!TRIED.equals(log.state())) {
                    // 이미 되돌렸거나(Cancel 재시도), 확정된 요청이다.
                    wallet.commit();
                    return;
                }

                execute(wallet, """
                        UPDATE wallet
                           SET balance = balance + %d,
                               frozen = frozen - %d
                         WHERE user_id = %d"""
                        .formatted(log.amount(), log.amount(), log.userId()));
                execute(wallet, "UPDATE wallet_tcc_log SET state = '%s' WHERE request_id = %d"
                        .formatted(CANCELLED, requestId));
                wallet.commit();
            } catch (SQLException e) {
                wallet.rollback();
                throw e;
            }
        }
    }

    /**
     * 로그 행을 잠그고 읽는다. 기록이 없으면 null.
     *
     * <p>{@code FOR UPDATE}로 읽는 이유는 같은 요청이 동시에 두 번 들어와도 하나씩 처리되게 하기
     * 위해서다. 상태를 보고 자원을 옮기기까지가 한 트랜잭션이어야, 둘 다 TRIED를 보고 각자 돈을
     * 되돌리는 일이 없다.
     */
    private static Log lock(Connection wallet, long requestId) throws SQLException {
        String sql = "SELECT state, user_id, amount FROM wallet_tcc_log WHERE request_id = %d FOR UPDATE"
                .formatted(requestId);
        try (Statement statement = wallet.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return new Log(
                    resultSet.getString("state"),
                    resultSet.getLong("user_id"),
                    resultSet.getLong("amount"));
        }
    }

    private record Log(String state, long userId, long amount) {
    }
}
