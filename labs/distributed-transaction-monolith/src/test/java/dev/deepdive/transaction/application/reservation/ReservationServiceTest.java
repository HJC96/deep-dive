package dev.deepdive.transaction.application.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.transaction.domain.reservation.Reservation;
import dev.deepdive.transaction.domain.reservation.ReservationStatus;
import dev.deepdive.transaction.domain.seat.NotEnoughSeatsException;
import dev.deepdive.transaction.domain.seat.WorkshopSeat;
import dev.deepdive.transaction.domain.wallet.NotEnoughCreditException;
import dev.deepdive.transaction.domain.wallet.Wallet;
import dev.deepdive.transaction.infrastructure.persistence.reservation.ReservationRepository;
import dev.deepdive.transaction.infrastructure.persistence.seat.WorkshopSeatRepository;
import dev.deepdive.transaction.infrastructure.persistence.wallet.WalletLedgerRepository;
import dev.deepdive.transaction.infrastructure.persistence.wallet.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:monolith;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReservationServiceTest {
    static final long WORKSHOP_ID = 100L;
    static final long USER_ID = 1L;

    @Autowired ReservationService service;
    @Autowired ReservationRepository reservations;
    @Autowired WorkshopSeatRepository workshops;
    @Autowired WalletRepository wallets;
    @Autowired WalletLedgerRepository ledgers;

    @BeforeEach
    void reset() {
        ledgers.deleteAll();
        reservations.deleteAll();
        wallets.deleteAll();
        workshops.deleteAll();
    }

    @Test
    void createOnlyStoresReservation() {
        seed(10, 100_000);

        ReservationResult result = createReservation();

        assertThat(result.status()).isEqualTo(ReservationStatus.CREATED);
        assertThat(result.amount()).isZero();
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(workshop().getReservedCount()).isZero();
        assertThat(wallet().getBalance()).isEqualTo(100_000);
        assertThat(ledgers.count()).isZero();
    }

    @Test
    void placeChangesSeatWalletAndReservationTogether() {
        seed(10, 100_000);
        long reservationId = createReservation().reservationId();

        ReservationResult result = service.place(reservationId);

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.amount()).isEqualTo(60_000);
        assertThat(reservation(reservationId).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(workshop().getReservedCount()).isEqualTo(2);
        assertThat(wallet().getBalance()).isEqualTo(40_000);
        assertThat(ledgers.count()).isEqualTo(1);
    }

    @Test
    void insufficientCreditKeepsCreatedReservationAndRollsBackSeats() {
        seed(10, 50_000);
        long reservationId = createReservation().reservationId();

        assertThatThrownBy(() -> service.place(reservationId))
                .isInstanceOf(NotEnoughCreditException.class)
                .hasMessage("not enough credit");

        assertThat(reservation(reservationId).getStatus()).isEqualTo(ReservationStatus.CREATED);
        assertThat(workshop().getReservedCount()).isZero();
        assertThat(wallet().getBalance()).isEqualTo(50_000);
        assertThat(ledgers.count()).isZero();
    }

    @Test
    void insufficientSeatsKeepsCreatedReservationAndChangesNothing() {
        seed(1, 100_000);
        long reservationId = createReservation().reservationId();

        assertThatThrownBy(() -> service.place(reservationId))
                .isInstanceOf(NotEnoughSeatsException.class)
                .hasMessage("not enough seats");

        assertThat(reservation(reservationId).getStatus()).isEqualTo(ReservationStatus.CREATED);
        assertThat(workshop().getReservedCount()).isZero();
        assertThat(wallet().getBalance()).isEqualTo(100_000);
        assertThat(ledgers.count()).isZero();
    }

    @Test
    void placingConfirmedReservationReturnsExistingResult() {
        seed(10, 100_000);
        long reservationId = createReservation().reservationId();

        ReservationResult first = service.place(reservationId);
        ReservationResult second = service.place(reservationId);

        assertThat(second).isEqualTo(first);
        assertThat(workshop().getReservedCount()).isEqualTo(2);
        assertThat(wallet().getBalance()).isEqualTo(40_000);
        assertThat(ledgers.count()).isEqualTo(1);
    }

    @Test
    void concurrentPlaceRequestsProcessReservationOnce() throws Exception {
        seed(10, 100_000);
        long reservationId = createReservation().reservationId();
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.place(reservationId);
                }));
            }
            ready.await();
            start.countDown();

            List<ReservationResult> results = new ArrayList<>();
            for (Future<ReservationResult> future : futures) {
                results.add(future.get());
            }

            assertThat(results).allMatch(results.getFirst()::equals);
            assertThat(workshop().getReservedCount()).isEqualTo(2);
            assertThat(wallet().getBalance()).isEqualTo(40_000);
            assertThat(ledgers.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unknownReservationCannotBePlaced() {
        assertThatThrownBy(() -> service.place(999L))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("reservation not found: 999");
    }

    private void seed(int capacity, long balance) {
        workshops.save(new WorkshopSeat(WORKSHOP_ID, "Java Transaction Workshop", capacity, 30_000));
        wallets.save(new Wallet(USER_ID, balance));
    }

    private ReservationResult createReservation() {
        return service.create(new CreateReservationCommand(USER_ID, WORKSHOP_ID, 2));
    }

    private Reservation reservation(long reservationId) {
        return reservations.findById(reservationId).orElseThrow();
    }

    private WorkshopSeat workshop() {
        return workshops.findById(WORKSHOP_ID).orElseThrow();
    }

    private Wallet wallet() {
        return wallets.findById(USER_ID).orElseThrow();
    }
}
