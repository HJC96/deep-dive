package dev.deepdive.transaction.msa.seat.application;

public class WorkshopNotFoundException extends RuntimeException {
    public WorkshopNotFoundException(long workshopId) {
        super("workshop not found: " + workshopId);
    }
}
