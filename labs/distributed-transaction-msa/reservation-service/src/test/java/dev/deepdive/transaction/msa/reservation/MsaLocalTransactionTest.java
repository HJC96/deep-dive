package dev.deepdive.transaction.msa.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.transaction.msa.reservation.application.ReservationService;
import dev.deepdive.transaction.msa.reservation.application.dto.command.CreateReservationCommand;
import dev.deepdive.transaction.msa.reservation.application.dto.result.ReservationResult;
import dev.deepdive.transaction.msa.reservation.domain.ReservationStatus;
import dev.deepdive.transaction.msa.reservation.infrastructure.repository.ReservationRepository;
import dev.deepdive.transaction.msa.seat.SeatServiceApplication;
import dev.deepdive.transaction.msa.seat.domain.WorkshopSeat;
import dev.deepdive.transaction.msa.seat.infrastructure.repository.SeatReservationHistoryRepository;
import dev.deepdive.transaction.msa.seat.infrastructure.repository.WorkshopSeatRepository;
import dev.deepdive.transaction.msa.wallet.WalletServiceApplication;
import dev.deepdive.transaction.msa.wallet.domain.Wallet;
import dev.deepdive.transaction.msa.wallet.infrastructure.repository.WalletLedgerRepository;
import dev.deepdive.transaction.msa.wallet.infrastructure.repository.WalletRepository;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.HttpClientErrorException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MsaLocalTransactionTest {
    private static final long WORKSHOP_ID = 100L;
    private static final long USER_ID = 1L;

    private ConfigurableApplicationContext seatContext;
    private ConfigurableApplicationContext walletContext;
    private ConfigurableApplicationContext reservationContext;

    private WorkshopSeatRepository workshopRepository;
    private SeatReservationHistoryRepository seatHistoryRepository;
    private WalletRepository walletRepository;
    private WalletLedgerRepository walletLedgerRepository;
    private ReservationRepository reservationRepository;
    private ReservationService reservationService;

    @BeforeAll
    void startServices() {
        seatContext = start(SeatServiceApplication.class, "msa-seat");
        walletContext = start(WalletServiceApplication.class, "msa-wallet");

        int seatPort = portOf(seatContext);
        int walletPort = portOf(walletContext);
        reservationContext = new SpringApplicationBuilder(ReservationServiceApplication.class)
                .run(reservationArguments(seatPort, walletPort));

        workshopRepository = seatContext.getBean(WorkshopSeatRepository.class);
        seatHistoryRepository = seatContext.getBean(SeatReservationHistoryRepository.class);
        walletRepository = walletContext.getBean(WalletRepository.class);
        walletLedgerRepository = walletContext.getBean(WalletLedgerRepository.class);
        reservationRepository = reservationContext.getBean(ReservationRepository.class);
        reservationService = reservationContext.getBean(ReservationService.class);
    }

    @AfterAll
    void stopServices() {
        if (reservationContext != null) {
            reservationContext.close();
        }
        if (walletContext != null) {
            walletContext.close();
        }
        if (seatContext != null) {
            seatContext.close();
        }
    }

    @BeforeEach
    void resetDatabases() {
        reservationRepository.deleteAll();
        seatHistoryRepository.deleteAll();
        workshopRepository.deleteAll();
        walletLedgerRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void allServicesChangeTheirOwnDatabaseWhenEveryCallSucceeds() {
        seed(100_000);
        long reservationId = createReservation();

        ReservationResult result = reservationService.place(reservationId);

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(workshopRepository.findById(WORKSHOP_ID).orElseThrow().getReservedCount()).isEqualTo(2);
        assertThat(seatHistoryRepository.count()).isEqualTo(1);
        assertThat(walletRepository.findById(USER_ID).orElseThrow().getBalance()).isEqualTo(40_000);
        assertThat(walletLedgerRepository.count()).isEqualTo(1);
    }

    @Test
    void walletFailureCannotRollBackTheSeatServiceCommit() {
        seed(50_000);
        long reservationId = createReservation();

        assertThatThrownBy(() -> reservationService.place(reservationId))
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("409");

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CREATED);
        assertThat(workshopRepository.findById(WORKSHOP_ID).orElseThrow().getReservedCount()).isEqualTo(2);
        assertThat(seatHistoryRepository.count()).isEqualTo(1);
        assertThat(walletRepository.findById(USER_ID).orElseThrow().getBalance()).isEqualTo(50_000);
        assertThat(walletLedgerRepository.count()).isZero();
    }

    private ConfigurableApplicationContext start(Class<?> applicationClass, String databaseName) {
        return new SpringApplicationBuilder(applicationClass)
                .run(commonArguments(databaseName));
    }

    private String[] reservationArguments(int seatPort, int walletPort) {
        String[] commonArguments = commonArguments("msa-reservation");
        String[] arguments = Arrays.copyOf(commonArguments, commonArguments.length + 2);
        arguments[commonArguments.length] = "--services.seat.base-url=http://localhost:" + seatPort;
        arguments[commonArguments.length + 1] = "--services.wallet.base-url=http://localhost:" + walletPort;
        return arguments;
    }

    private String[] commonArguments(String databaseName) {
        return new String[] {
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.open-in-view=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN"
        };
    }

    private int portOf(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private void seed(long walletBalance) {
        workshopRepository.save(new WorkshopSeat(
                WORKSHOP_ID, "Java MSA Workshop", 10, 30_000));
        walletRepository.save(new Wallet(USER_ID, walletBalance));
    }

    private long createReservation() {
        return reservationService.create(new CreateReservationCommand(USER_ID, WORKSHOP_ID, 2))
                .reservationId();
    }
}
