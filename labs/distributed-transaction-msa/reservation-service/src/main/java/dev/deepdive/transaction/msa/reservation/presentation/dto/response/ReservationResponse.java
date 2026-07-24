package dev.deepdive.transaction.msa.reservation.presentation.dto.response;

import dev.deepdive.transaction.msa.reservation.application.dto.result.ReservationResult;
import dev.deepdive.transaction.msa.reservation.domain.ReservationStatus;

public record ReservationResponse(long reservationId, long amount, ReservationStatus status) {
    public static ReservationResponse from(ReservationResult result) {
        return new ReservationResponse(result.reservationId(), result.amount(), result.status());
    }
}
