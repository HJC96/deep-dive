package dev.deepdive.transaction.msa.wallet.domain;

public class NotEnoughCreditException extends RuntimeException {
    public NotEnoughCreditException() {
        super("not enough credit");
    }
}
