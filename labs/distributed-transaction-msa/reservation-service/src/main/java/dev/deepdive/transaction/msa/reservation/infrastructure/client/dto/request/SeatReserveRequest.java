package dev.deepdive.transaction.msa.reservation.infrastructure.client.dto.request;

public record SeatReserveRequest(long requestId, long workshopId, int seatCount) {}
