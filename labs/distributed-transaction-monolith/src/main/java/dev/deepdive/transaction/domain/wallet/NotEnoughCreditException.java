package dev.deepdive.transaction.domain.wallet;

public class NotEnoughCreditException extends RuntimeException {
    public NotEnoughCreditException() {
        super("not enough credit");
    }
}
