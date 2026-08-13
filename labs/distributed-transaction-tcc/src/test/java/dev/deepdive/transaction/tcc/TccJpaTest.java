package dev.deepdive.transaction.tcc;

import dev.deepdive.transaction.tcc.seat.SeatParticipant;
import dev.deepdive.transaction.tcc.seat.SeatTccLog;
import dev.deepdive.transaction.tcc.seat.SeatTccLogRepository;
import dev.deepdive.transaction.tcc.seat.WorkshopSeat;
import dev.deepdive.transaction.tcc.seat.WorkshopSeatRepository;
import dev.deepdive.transaction.tcc.wallet.Wallet;
import dev.deepdive.transaction.tcc.wallet.WalletParticipant;
import dev.deepdive.transaction.tcc.wallet.WalletTccLog;
import dev.deepdive.transaction.tcc.wallet.WalletTccLogRepository;
import dev.deepdive.transaction.tcc.wallet.WalletRepository;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 서로 다른 두 대의 MySQL과 그 위에 올린 JPA 한 벌씩을 준비한다. 좌석은 {@code seat_db}에, 지갑은
 * {@code wallet_db}에 있다.
 *
 * <p>{@code distributed-transaction-2pc}와 달리 XA를 쓰지 않는다. 두 EntityManagerFactory는 서로를
 * 모르고, 참여자가 각자 자기 로컬 트랜잭션만 커밋한다.
 *
 * <p><b>테스트 클래스에 {@code @Transactional}을 붙이지 않는다.</b> 붙이면 테스트 메서드 하나가 통째로
 * 한 트랜잭션이 되어, 참여자가 단계마다 커밋한다는 이 실험실의 전제가 무너진다. 검증 메서드가 매번
 * 새 트랜잭션으로 읽어야 "이미 커밋되어 남에게도 보이는 값"을 확인할 수 있다.
 */
@SpringBootTest
abstract class TccJpaTest {

    static final long WORKSHOP_ID = 100L;
    static final long OTHER_WORKSHOP_ID = 200L;
    static final long USER_ID = 1L;
    static final long REQUEST_ID = 1L;
    static final int SEAT_COUNT = 2;
    static final int TOTAL_SEATS = 10;
    static final long PRICE = 30_000L;
    static final long AMOUNT = SEAT_COUNT * PRICE;

    // 싱글톤 컨테이너 패턴: 한 번 start 후 stop하지 않는다.
    // 컨테이너는 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
    private static final MySQLContainer SEAT = mysql("seat_db");
    private static final MySQLContainer WALLET = mysql("wallet_db");

    static {
        SEAT.start();
        WALLET.start();
    }

    @Autowired
    SeatParticipant seatParticipant;

    @Autowired
    WalletParticipant walletParticipant;

    @Autowired
    WorkshopSeatRepository seats;

    @Autowired
    SeatTccLogRepository seatLogs;

    @Autowired
    WalletRepository wallets;

    @Autowired
    WalletTccLogRepository walletLogs;

    /** 락이 걸리는지 직접 재는 테스트에서만 쓴다. JPA를 거치지 않아야 참여자와 무관한 남의 커넥션이 된다. */
    @Autowired
    @Qualifier("seatDataSource")
    DataSource seatDataSource;

    private static MySQLContainer mysql(String databaseName) {
        return new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName(databaseName)
                .withUsername("root")
                .withPassword("test");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("tcc.seat.datasource.url", SEAT::getJdbcUrl);
        registry.add("tcc.seat.datasource.username", SEAT::getUsername);
        registry.add("tcc.seat.datasource.password", SEAT::getPassword);
        registry.add("tcc.wallet.datasource.url", WALLET::getJdbcUrl);
        registry.add("tcc.wallet.datasource.username", WALLET::getUsername);
        registry.add("tcc.wallet.datasource.password", WALLET::getPassword);
    }

    /**
     * 표를 비우고 시드를 다시 넣는다.
     *
     * <p>테이블 자체는 Hibernate가 {@code create-drop}으로 만들어 두므로 여기서는 행만 갈아 끼운다.
     */
    void seed(long walletBalance) {
        seatLogs.deleteAllInBatch();
        seats.deleteAllInBatch();
        walletLogs.deleteAllInBatch();
        wallets.deleteAllInBatch();

        seats.saveAll(List.of(
                new WorkshopSeat(WORKSHOP_ID, "Java TCC Workshop", TOTAL_SEATS, PRICE),
                new WorkshopSeat(OTHER_WORKSHOP_ID, "Kotlin TCC Workshop", TOTAL_SEATS, PRICE)));
        wallets.save(new Wallet(USER_ID, walletBalance));
    }

    int reservedCount() {
        return seats.findById(WORKSHOP_ID).orElseThrow().getReservedCount();
    }

    int heldCount() {
        return seats.findById(WORKSHOP_ID).orElseThrow().getHeldCount();
    }

    long balance() {
        return wallets.findById(USER_ID).orElseThrow().getBalance();
    }

    long frozen() {
        return wallets.findById(USER_ID).orElseThrow().getFrozen();
    }

    /** 요청이 어디까지 갔는지 참여자가 적어 둔 상태. 로그가 아예 없으면 null이다. */
    String seatLogState(long requestId) {
        return seatLogs.findById(requestId).map(SeatTccLog::getState).orElse(null);
    }

    String walletLogState(long requestId) {
        return walletLogs.findById(requestId).map(WalletTccLog::getState).orElse(null);
    }
}
