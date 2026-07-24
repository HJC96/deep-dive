package dev.deepdive.transaction.msa.reservation.application.dto.result;

import dev.deepdive.transaction.msa.reservation.domain.Reservation;
import dev.deepdive.transaction.msa.reservation.domain.ReservationStatus;

public record ReservationResult(long reservationId, long amount, ReservationStatus status) {
    public static ReservationResult from(Reservation reservation) {
        return new ReservationResult(reservation.getId(), reservation.getAmount(), reservation.getStatus());
    }
}
