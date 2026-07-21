package dev.deepdive.transaction.presentation.reservation.dto;

import dev.deepdive.transaction.application.reservation.CreateReservationCommand;
import jakarta.validation.constraints.Min;

public record CreateReservationRequest(
        @Min(1) long userId,
        @Min(1) long workshopId,
        @Min(1) int seatCount
) {
    public CreateReservationCommand toCommand() {
        return new CreateReservationCommand(userId, workshopId, seatCount);
    }
}
