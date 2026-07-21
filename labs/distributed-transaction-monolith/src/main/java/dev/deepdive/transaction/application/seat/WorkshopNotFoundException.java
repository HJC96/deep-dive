package dev.deepdive.transaction.application.seat;

public class WorkshopNotFoundException extends RuntimeException {
    public WorkshopNotFoundException(long workshopId) {
        super("workshop not found: " + workshopId);
    }
}
