package dev.deepdive.transaction.application.reservation.dto.result;

import dev.deepdive.transaction.domain.reservation.Reservation;
import dev.deepdive.transaction.domain.reservation.ReservationStatus;

public record ReservationResult(long reservationId, long amount, ReservationStatus status) {
    public static ReservationResult from(Reservation reservation) {
        return new ReservationResult(reservation.getId(), reservation.getAmount(), reservation.getStatus());
    }
}
