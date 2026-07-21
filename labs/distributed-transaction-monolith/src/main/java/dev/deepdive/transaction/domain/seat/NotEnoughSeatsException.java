package dev.deepdive.transaction.domain.seat;

public class NotEnoughSeatsException extends RuntimeException {
    public NotEnoughSeatsException() {
        super("not enough seats");
    }
}
