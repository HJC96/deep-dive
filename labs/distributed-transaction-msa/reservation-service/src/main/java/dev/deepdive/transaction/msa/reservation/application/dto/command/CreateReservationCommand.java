package dev.deepdive.transaction.msa.reservation.application.dto.command;

public record CreateReservationCommand(long userId, long workshopId, int seatCount) {}
