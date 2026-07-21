package dev.deepdive.transaction.application.wallet;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(long userId) {
        super("wallet not found: " + userId);
    }
}
