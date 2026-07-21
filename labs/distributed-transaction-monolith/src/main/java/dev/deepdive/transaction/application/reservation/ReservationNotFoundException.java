package dev.deepdive.transaction.application.reservation;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(long reservationId) {
        super("reservation not found: " + reservationId);
    }
}
