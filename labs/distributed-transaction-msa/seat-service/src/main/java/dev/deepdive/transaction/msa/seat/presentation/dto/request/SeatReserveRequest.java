package dev.deepdive.transaction.msa.seat.presentation.dto.request;

import jakarta.validation.constraints.Min;

public record SeatReserveRequest(
        @Min(1) long requestId,
        @Min(1) long workshopId,
        @Min(1) int seatCount
) {}
