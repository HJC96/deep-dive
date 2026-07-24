package dev.deepdive.transaction.msa.reservation.application;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(long reservationId) {
        super("reservation not found: " + reservationId);
    }
}
