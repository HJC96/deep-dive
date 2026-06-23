package dev.deepdive.seat.core;

public record SeatReservationRequestResult(long userId, String seatNo, SeatReservationStatus status) {

    public boolean accepted() {
        return status == SeatReservationStatus.ACCEPTED;
    }
}
