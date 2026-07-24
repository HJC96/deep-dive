package dev.deepdive.transaction.application.reservation;

import dev.deepdive.transaction.application.reservation.dto.command.CreateReservationCommand;
import dev.deepdive.transaction.application.reservation.dto.result.ReservationResult;
import dev.deepdive.transaction.domain.reservation.Reservation;
import dev.deepdive.transaction.infrastructure.repository.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository repository;
    private final ReservationTransaction reservationTransaction;

    public ReservationService(ReservationRepository repository, ReservationTransaction reservationTransaction) {
        this.repository = repository;
        this.reservationTransaction = reservationTransaction;
    }

    @Transactional
    public ReservationResult create(CreateReservationCommand command) {
        Reservation reservation = repository.save(new Reservation(
                command.userId(), command.workshopId(), command.seatCount()));
        return ReservationResult.from(reservation);
    }

    public ReservationResult place(long reservationId) {
        return reservationTransaction.place(reservationId);
    }
}
