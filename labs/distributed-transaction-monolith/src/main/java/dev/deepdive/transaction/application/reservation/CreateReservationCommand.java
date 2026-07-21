package dev.deepdive.transaction.application.reservation;

public record CreateReservationCommand(long userId, long workshopId, int seatCount) {
    public CreateReservationCommand {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (workshopId <= 0) throw new IllegalArgumentException("workshopId must be positive");
        if (seatCount <= 0) throw new IllegalArgumentException("seatCount must be positive");
    }
}
