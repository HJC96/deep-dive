package dev.deepdive.transaction.msa.reservation.application;

import dev.deepdive.transaction.msa.reservation.application.dto.command.CreateReservationCommand;
import dev.deepdive.transaction.msa.reservation.application.dto.result.ReservationResult;
import dev.deepdive.transaction.msa.reservation.domain.Reservation;
import dev.deepdive.transaction.msa.reservation.domain.ReservationStatus;
import dev.deepdive.transaction.msa.reservation.infrastructure.client.SeatClient;
import dev.deepdive.transaction.msa.reservation.infrastructure.client.WalletClient;
import dev.deepdive.transaction.msa.reservation.infrastructure.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final SeatClient seatClient;
    private final WalletClient walletClient;

    public ReservationService(
            ReservationRepository reservationRepository,
            SeatClient seatClient,
            WalletClient walletClient
    ) {
        this.reservationRepository = reservationRepository;
        this.seatClient = seatClient;
        this.walletClient = walletClient;
    }

    @Transactional
    public ReservationResult create(CreateReservationCommand command) {
        Reservation reservation = reservationRepository.save(new Reservation(
                command.userId(), command.workshopId(), command.seatCount()));
        return ReservationResult.from(reservation);
    }

    @Transactional
    public ReservationResult place(long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return ReservationResult.from(reservation);
        }

        long amount = seatClient.reserve(
                reservationId, reservation.getWorkshopId(), reservation.getSeatCount());
        walletClient.debit(reservationId, reservation.getUserId(), amount);
        reservation.confirm(amount);
        return ReservationResult.from(reservation);
    }
}
