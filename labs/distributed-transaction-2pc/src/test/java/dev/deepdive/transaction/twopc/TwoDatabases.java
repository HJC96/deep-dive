package dev.deepdive.transaction.twopc;

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
 * <p>H2로는 이 실험을 못 한다. {@code XA PREPARE}로 멈춰 둔 트랜잭션이 커넥션이 끊긴 뒤에도
 * 살아 있어야 하고 {@code XA RECOVER}로 찾을 수 있어야 하는데, 그걸 지원하는 게 MySQL이다.
 *
 * <p>모든 커넥션을 {@code root}로 연다. MySQL 8에서 {@code XA RECOVER}는
 * {@code XA_RECOVER_ADMIN} 권한을 요구한다.
 */
final class TwoDatabases {

    static final long WORKSHOP_ID = 100L;
    static final long OTHER_WORKSHOP_ID = 200L;
    static final long USER_ID = 1L;
    static final long REQUEST_ID = 1L;
    static final int SEAT_COUNT = 2;
    static final long PRICE = 30_000L;
    static final long AMOUNT = SEAT_COUNT * PRICE;

    // 글로벌 트랜잭션 하나(gtrid = reservation-1)에 참여자별 브랜치(bqual)가 붙는다.
    static final String SEAT_XID = "'reservation-1','seat'";
    static final String WALLET_XID = "'reservation-1','wallet'";

    // 싱글톤 컨테이너 패턴: 한 번 start 후 stop하지 않는다.
    // 컨테이너는 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
    private static final MySQLContainer SEAT = mysql("seat_db");
    private static final MySQLContainer WALLET = mysql("wallet_db");

    static {
        SEAT.start();
        WALLET.start();
    }

    private TwoDatabases() {
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

    /**
     * 테이블을 다시 만들고 시드를 넣는다.
     *
     * <p>앞선 테스트가 prepared 상태로 남긴 트랜잭션을 먼저 정리한다. 그게 남아 있으면 락도 남아 있어서
     * {@code DROP TABLE}부터 막힌다. 실험 자체가 그 상황을 만들기 때문에 매번 확인한다.
     */
    static void seed(long walletBalance) throws SQLException {
        rollbackPrepared(SEAT, SEAT_XID);
        rollbackPrepared(WALLET, WALLET_XID);

        try (Connection seat = seat(); Connection wallet = wallet()) {
            execute(seat, "DROP TABLE IF EXISTS workshop_seat");
            execute(seat, """
                    CREATE TABLE workshop_seat (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        total_seats INT NOT NULL,
                        price BIGINT NOT NULL,
                        reserved_count INT NOT NULL
                    )""");
            execute(seat, "INSERT INTO workshop_seat VALUES (%d, 'Java 2PC Workshop', 10, %d, 0)"
                    .formatted(WORKSHOP_ID, PRICE));
            // 락이 행 단위로 걸리는지 보려면 건드리지 않는 워크숍이 하나 더 있어야 한다.
            execute(seat, "INSERT INTO workshop_seat VALUES (%d, 'Kotlin 2PC Workshop', 10, %d, 0)"
                    .formatted(OTHER_WORKSHOP_ID, PRICE));

            // MSA 실험실의 SeatReservationHistory와 같은 역할. 어느 예약에서 나온 좌석 처리인지 남긴다.
            execute(seat, "DROP TABLE IF EXISTS seat_reservation_history");
            execute(seat, """
                    CREATE TABLE seat_reservation_history (
                        request_id BIGINT PRIMARY KEY,
                        workshop_id BIGINT NOT NULL,
                        seat_count INT NOT NULL,
                        amount BIGINT NOT NULL
                    )""");

            execute(wallet, "DROP TABLE IF EXISTS wallet");
            execute(wallet, """
                    CREATE TABLE wallet (
                        user_id BIGINT PRIMARY KEY,
                        balance BIGINT NOT NULL
                    )""");
            execute(wallet, "INSERT INTO wallet VALUES (%d, %d)".formatted(USER_ID, walletBalance));
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

    /**
     * {@code seat_db}에서 커밋도 롤백도 안 된 채 멈춰 있는 트랜잭션의 수.
     *
     * <p>{@code XA RECOVER}가 바로 이 목록을 보여 준다. 운영자가 멈춘 트랜잭션을 찾을 때 쓰는 문이고,
     * prepare한 트랜잭션이 커넥션이 끊긴 뒤에도 살아 있다는 증거다.
     */
    static int preparedSeatCount() throws SQLException {
        try (Connection connection = seat();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("XA RECOVER")) {
            int count = 0;
            while (resultSet.next()) {
                count++;
            }
            return count;
        }
    }

    private static Connection connect(MySQLContainer container) throws SQLException {
        return DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static void rollbackPrepared(MySQLContainer container, String xid) throws SQLException {
        try (Connection connection = connect(container)) {
            execute(connection, "XA ROLLBACK " + xid);
        } catch (SQLException e) {
            // 멈춰 있는 게 없으면 XAER_NOTA가 난다. 정리할 게 없었다는 뜻이라 넘어간다.
        }
    }
}
