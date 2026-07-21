package dev.deepdive.transaction.application.reservation;

import dev.deepdive.transaction.application.seat.SeatService;
import dev.deepdive.transaction.application.wallet.WalletService;
import dev.deepdive.transaction.domain.reservation.Reservation;
import dev.deepdive.transaction.domain.reservation.ReservationStatus;
import dev.deepdive.transaction.infrastructure.persistence.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTransaction {
    private final ReservationRepository repository;
    private final SeatService seatService;
    private final WalletService walletService;

    public ReservationTransaction(
            ReservationRepository repository,
            SeatService seatService,
            WalletService walletService
    ) {
        this.repository = repository;
        this.seatService = seatService;
        this.walletService = walletService;
    }

    @Transactional
    public ReservationResult place(long reservationId) {
        Reservation reservation = repository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return ReservationResult.from(reservation);
        }

        long amount = seatService.reserve(reservation.getWorkshopId(), reservation.getSeatCount());
        String ledgerKey = "reservation:" + reservationId + ":debit";
        walletService.debit(reservation.getUserId(), amount, ledgerKey);
        reservation.confirm(amount);
        return ReservationResult.from(reservation);
    }
}
