package dev.deepdive.transaction.tcc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 서로 다른 두 대의 MySQL. 좌석은 {@code seat_db}에, 지갑은 {@code wallet_db}에 있다.
 *
 * <p>{@code distributed-transaction-2pc}와 달리 XA를 쓰지 않는다. TCC는 각 참여자가 자기 로컬
 * 트랜잭션을 곧바로 커밋하고, 되돌릴 일이 생기면 반대 방향 업무 작업으로 취소한다.
 *
 * <p>그래서 스키마에 "확정"과 "잡아 둠"을 나누는 칸이 하나씩 더 있다. 좌석은
 * {@code reserved_count}와 {@code held_count}, 지갑은 {@code balance}와 {@code frozen}이다.
 */
final class TccDatabases {

    static final long WORKSHOP_ID = 100L;
    static final long OTHER_WORKSHOP_ID = 200L;
    static final long USER_ID = 1L;
    static final long REQUEST_ID = 1L;
    static final int SEAT_COUNT = 2;
    static final int TOTAL_SEATS = 10;
    static final long PRICE = 30_000L;
    static final long AMOUNT = SEAT_COUNT * PRICE;

    // 참여자가 자기 요청을 어떤 상태로 기록해 두는지. 멱등 처리와 빈 취소 판단의 근거다.
    static final String TRIED = "TRIED";
    static final String CONFIRMED = "CONFIRMED";
    static final String CANCELLED = "CANCELLED";

    // 싱글톤 컨테이너 패턴: 한 번 start 후 stop하지 않는다.
    // 컨테이너는 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
    private static final MySQLContainer SEAT = mysql("seat_db");
    private static final MySQLContainer WALLET = mysql("wallet_db");

    static {
        SEAT.start();
        WALLET.start();
    }

    private TccDatabases() {
    }

    private static MySQLContainer mysql(String databaseName) {
        return new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName(databaseName)
                .withUsername("root")
                .withPassword("test");
    }

    static Connection seat() throws SQLException {
        return connect(SEAT);
    }

    static Connection wallet() throws SQLException {
        return connect(WALLET);
    }

    /** 테이블을 다시 만들고 시드를 넣는다. */
    static void seed(long walletBalance) throws SQLException {
        try (Connection seat = seat(); Connection wallet = wallet()) {
            execute(seat, "DROP TABLE IF EXISTS workshop_seat");
            execute(seat, """
                    CREATE TABLE workshop_seat (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        total_seats INT NOT NULL,
                        price BIGINT NOT NULL,
                        reserved_count INT NOT NULL,
                        held_count INT NOT NULL
                    )""");
            execute(seat, "INSERT INTO workshop_seat VALUES (%d, 'Java TCC Workshop', %d, %d, 0, 0)"
                    .formatted(WORKSHOP_ID, TOTAL_SEATS, PRICE));
            execute(seat, "INSERT INTO workshop_seat VALUES (%d, 'Kotlin TCC Workshop', %d, %d, 0, 0)"
                    .formatted(OTHER_WORKSHOP_ID, TOTAL_SEATS, PRICE));

            // 요청 하나가 어디까지 갔는지 참여자가 스스로 적어 둔다.
            // 이 기록이 없으면 Confirm/Cancel을 두 번 받았는지, Try가 아예 없었는지 알 수 없다.
            execute(seat, "DROP TABLE IF EXISTS seat_tcc_log");
            execute(seat, """
                    CREATE TABLE seat_tcc_log (
                        request_id BIGINT PRIMARY KEY,
                        workshop_id BIGINT NOT NULL,
                        seat_count INT NOT NULL,
                        state VARCHAR(20) NOT NULL
                    )""");

            execute(wallet, "DROP TABLE IF EXISTS wallet");
            execute(wallet, """
                    CREATE TABLE wallet (
                        user_id BIGINT PRIMARY KEY,
                        balance BIGINT NOT NULL,
                        frozen BIGINT NOT NULL
                    )""");
            execute(wallet, "INSERT INTO wallet VALUES (%d, %d, 0)".formatted(USER_ID, walletBalance));

            execute(wallet, "DROP TABLE IF EXISTS wallet_tcc_log");
            execute(wallet, """
                    CREATE TABLE wallet_tcc_log (
                        request_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        amount BIGINT NOT NULL,
                        state VARCHAR(20) NOT NULL
                    )""");
        }
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /** 값이 없으면 null. 참여자가 요청 상태를 조회할 때 쓴다. */
    static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static Connection connect(MySQLContainer container) throws SQLException {
        return DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
