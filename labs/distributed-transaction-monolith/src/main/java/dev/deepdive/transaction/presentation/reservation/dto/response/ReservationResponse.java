package dev.deepdive.transaction.presentation.reservation.dto.response;

import dev.deepdive.transaction.application.reservation.dto.result.ReservationResult;
import dev.deepdive.transaction.domain.reservation.ReservationStatus;

public record ReservationResponse(long reservationId, long amount, ReservationStatus status) {
    public static ReservationResponse from(ReservationResult result) {
        return new ReservationResponse(result.reservationId(), result.amount(), result.status());
    }
}
