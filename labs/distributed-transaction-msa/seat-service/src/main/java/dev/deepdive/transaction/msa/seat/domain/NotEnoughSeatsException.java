package dev.deepdive.transaction.msa.seat.domain;

public class NotEnoughSeatsException extends RuntimeException {
    public NotEnoughSeatsException() {
        super("not enough seats");
    }
}
